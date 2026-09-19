# Threat Model

## Purpose

This document defines the main integrity and security risks considered in the prototype.

## Threats Considered

Direct modification of an audit event in the database

Direct deletion of a historical event

Deletion of the most recent event

Modification of stored hash values

Concurrent writes creating an invalid chain

Accidental application-level updates

Sensitive data being logged or exposed

Modification of exported audit data

A caller injecting a fake redaction marker into a payload to disrupt reads of that record
(Scenario B)

A redacted value being re-identifiable across fields/events via its commitment (Scenario B)

Unauthorized retention, redaction, or export requests (Scenario B — no auth layer exists)

A caller forging a compliance-relevant `CLIENT_ACCOUNT_ACCESS` event through the generic write
endpoint, since no event-type registry reserves that name for the dedicated Scenario C endpoint
(Scenario C)

A source application skipping or failing to call the access-recording endpoint, so an actual
client account access goes unrecorded with no way to detect the gap (Scenario C)

Unauthorized querying or export of client account access history, since the compliance endpoints
have no authentication or tenant isolation (Scenario C)

## Current Controls

Append-only application API

Cryptographic hash chain

Stored chain head

Database transactions

Row-level locking for chain-head updates

Validation of event input

No update or delete API for audit events

Structured verification endpoint

Encryption (AES-256-GCM) and HMAC-based commitments for redactable values, with independently
derived subkeys for each purpose (Scenario B, implemented)

Digital signatures (Ed25519) for export bundles, including a chain-anchor checkpoint so a
recipient has some anchor back to the real chain (Scenario B, implemented)

Commitments are bound to the redaction ID, event ID, and field path, not just the value, so
identical secrets don't produce identical commitments (Scenario B, implemented)

Hydration safely ignores a payload field that merely looks like a redaction marker rather than
throwing (Scenario B, implemented)

A fixed, validated request contract (`ClientAccountAccessRequest`) for client account access
events — required actor/account/action/outcome/source/request fields, a closed `Action`/`Outcome`
enum, and bounded field/list sizes — so a caller cannot place raw account content or unexpected
fields into a compliance event through the dedicated endpoint (Scenario C, implemented)

## Important Limitation

A database administrator with unrestricted access could potentially rewrite all audit records, recalculate all hashes, and update the stored chain head.

The current prototype detects partial or unauthorized changes to stored history, but it does not provide an external trust anchor.

A production version should periodically publish or store signed chain checkpoints outside the primary database.

## Known Open Gaps (Scenario B)

- **No authentication or authorization on any endpoint.** This is the single highest-severity gap:
  every endpoint, including irreversible field redaction and bulk data export, is reachable by any
  caller who can reach the service.
- **No associated-data binding on the AES-GCM ciphertext.** A ciphertext copied from one
  `audit_redaction_value` row into another (requiring direct database write access) would still
  decrypt successfully, since nothing ties it to its own `redactionId`/`eventId`/`fieldPath`.
- **No key rotation path.** Because commitments are embedded in the already-hashed, immutable
  payload, rotating `AUDIT_CRYPTOGRAPHIC_KEY` makes every previously issued commitment
  unverifiable against the new key. There is no versioned-key scheme to migrate around this.
- **Export signing key has no durable identity.** `ExportService` generates a fresh Ed25519
  keypair on every application startup, so bundles signed by different restarts or replicas can't
  be cross-verified, and a recipient has no independent way to know which public key to trust.
- **`redact()` accepts an invalid or nonexistent field path silently** (matches zero rows, returns
  `redactedFields: 0`) rather than rejecting it distinctly from "already redacted."

## Known Open Gaps (Scenario C)

Full detail and reasoning is in [Scenario C](scenario-c.md#what-is-not-implemented); summarized
here as threat-model entries:

- **No authentication, producer identity verification, or tenant isolation** on either the
  write (`POST /client-account-access`) or query (`GET /client-account-access`) endpoint. Any
  caller can submit or read client account access history. This is called out in
  `docs/scenario-c.md` as the highest-priority production gap for this scenario.
- **The generic `POST /api/v1/audit/events` endpoint can still forge a `CLIENT_ACCOUNT_ACCESS`
  event.** There is no event-type registry reserving that name for the dedicated, validated
  Scenario C contract — a caller who bypasses the typed endpoint can submit an arbitrary payload
  under the same event type.
- **No trusted linkage between an actual account read and the audit record describing it.**
  Recording depends entirely on the source application choosing to call the endpoint; there is no
  transactional or architectural guarantee that every real access produces a corresponding event,
  and no mechanism to detect a source application that silently stops reporting.
- **Reading or exporting compliance access events is not itself audited.** A compliance user (or
  anyone, given the lack of auth) querying or exporting this history leaves no trace in the log.
