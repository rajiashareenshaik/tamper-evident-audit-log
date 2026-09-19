# Scenario B: Retention, redaction, and export

Scenario B extends the audit log without weakening the append-only chain built in Scenario A.

## Retention

`POST /api/v1/audit/retention/archive-expired` archives records older than `audit.retention-window`
(365 days by default). Archiving writes a row to `audit_event_archive`; it does not update or delete the
original audit event. Normal event queries hide archived records, while chain verification deliberately
reads every physical event. Re-running the retention job is safe because the archive operation is
idempotent.

This is a soft archive, not storage reclamation. Keeping the original record is what lets the service prove
the full chain without inventing special gaps or trusted archive markers. A production system that moves
old rows to cold storage would need signed boundary checkpoints and a verifier able to read both stores.

## Structured redaction

Callers mark fields as redactable when the event is created:

```json
{
  "eventType": "ACCOUNT_UPDATED",
  "actorId": "user-17",
  "resourceType": "ACCOUNT",
  "resourceId": "account-42",
  "payload": { "profile": { "ssn": "123-45-6789" } },
  "redactableFields": ["profile.ssn"]
}
```

The field path uses dot notation and may point into nested objects. Before hashing, the service replaces the
plain value with a random redaction ID and a commitment `HMAC-SHA-256(commitmentKey, redactionId + eventId +
fieldPath + value)`. Binding the commitment to the redaction ID, event, and field path (not just the value)
means two fields holding the same underlying secret never produce the same commitment. The original value is
encrypted with AES-256-GCM and stored separately. Reads decrypt the value until redaction is requested:

```text
POST /api/v1/audit/events/{eventId}/redactions
{"fields":["profile.ssn"]}
```

Redaction destroys the stored ciphertext and records the redaction time. Later reads return
`{"redacted":true}` for that field. The immutable payload still contains the commitment, so its content hash
and every following chain hash remain unchanged. Redacting an already-redacted or never-redactable field path
is a safe no-op (it matches zero rows) rather than an error — the response doesn't distinguish "already
redacted" from "that path was never redactable," which is a known gap, not a security issue. Redaction is not
itself recorded as a new audit event in the chain.

The configured cryptographic key must be supplied through `AUDIT_CRYPTOGRAPHIC_KEY` outside local
development, and encryption/commitment use independently-derived subkeys of it (never the same raw key for
both). Production should use separate versioned keys in a key-management service — keys cannot be rotated
today without breaking already-issued commitments, since they're baked into `content_hash` forever. The
AES-GCM ciphertext also has no associated data binding it to its own `redactionId`/`eventId`/`fieldPath` row,
so a ciphertext copied between rows (which requires direct database write access) would still decrypt. Dot
notation does not currently address array elements or payload keys containing literal dots. Once ciphertext
is destroyed, the service cannot recover the value or prove what it was without somebody presenting the
original value.

## Bulk export

`GET /api/v1/audit/export` accepts exactly one of `actorId` or `resourceId`. The response contains every
matching record, including archived records, in ascending `sequence_id` order, with each record's content,
previous, and chain hashes. It also includes a `chainAnchor`: the genesis hash, and the global chain head's
hash/event count/sequence_id as of export time. The manifest's SHA-256 digest covers the records and the
chain anchor together, signed with Ed25519; the manifest includes the algorithm names, signature, and public
key. Recipients can recompute every content and chain hash, recompute the bundle digest, and verify the
signature without contacting this service.

Because export is filtered by actor or resource, exported records are not necessarily contiguous in the
global chain — other actors' or resources' events may sit between them there. A recipient can therefore
always verify each record's own hashes, and can verify that two *consecutive* exported records
(`sequence_id` differing by exactly 1) chain directly to each other. The `chainAnchor` is a checkpoint, not a
full inclusion proof: it lets a recipient confirm the export is consistent with a chain that had a given head
and event count at export time, and lets them confirm the very first exported record genuinely opens the
chain if its `previousHash` equals the anchor's `genesisHash`. It does not, by itself, prove how an exported
record that isn't chain-adjacent to its neighbor in the bundle relates to the intervening, unexported events —
that would require a full chain read (see `GET /api/v1/audit/verify`) or a Merkle-style inclusion proof, which
is out of scope here.

The signing key is generated when the application starts. That is suitable for demonstrating that a bundle
has not changed after export, but it is not a durable identity. A production deployment should load the
private key from a KMS/HSM and publish the public key through a trusted channel. A public key carried only
inside a bundle prevents unnoticed edits but does not, by itself, prove who created the bundle.

## Verification behavior

`GET /api/v1/audit/verify` uses a repeatable-read transaction and checks the complete physical chain. It
recomputes content hashes, previous links, chain hashes, event count, and the stored chain head. Retention
metadata and redaction ciphertext are outside the immutable event content, so legitimate archive and
redaction operations do not create false chain failures.
