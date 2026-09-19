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
