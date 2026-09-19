# Audit Log Service Requirements

## 1. Purpose

The goal of this service is to provide a reliable audit trail for important application and business events.

The service must allow applications to record audit events, search previously recorded events, and verify that historical records have not been modified or removed without detection.

The first version will use a single Spring Boot service backed by PostgreSQL. The design will favor correctness, traceability, and ease of verification over high write throughput.

The service will be developed in three stages.

Scenario A establishes the core append-only audit log and hash chain.

Scenario B extends the system with retention, redaction, and export capabilities.

Scenario C applies the audit platform to a less-defined compliance reporting requirement.

---

# 1a. Implementation Status and Deviations

Scenarios A and B are implemented. Scenario C is documented as a partial implementation with an explicit
scope boundary. This section records every place
the actual implementation deviates from the requirement text below, so this document stays an
honest record of original intent without being silently rewritten to match what was built.

**Pagination response shape (Section 17).** The API does not wrap results in
`{"items", "nextCursor", "hasMore"}`. `GET /api/v1/audit/events` returns a bare JSON array of
events. The cursor parameter is named `afterSequenceId`, not `cursor`. The default page size is
50, not 100 (the maximum of 500 matches).

**Time-range semantics (Section 16).** Both `from` and `to` are inclusive (`[from, to]`), not
`[from, to)` as specified. A request with `from` after `to` is not currently rejected — no such
validation exists.

**Verification failure types (Section 20).** `INVALID_GENESIS_REFERENCE` is not an implemented
failure type. The implemented set is `CONTENT_HASH_MISMATCH`, `PREVIOUS_HASH_MISMATCH`,
`CHAIN_HASH_MISMATCH`, `CHAIN_HEAD_MISMATCH`, and `CHAIN_COUNT_MISMATCH`.

**HTTP error response shape (Section 29).** The implemented shape is `{"error": "<message>"}`,
not `{"code", "message", "requestId", "timestamp"}`. Only `400 Bad Request` (via
`IllegalArgumentException`) is actually handled; `404`/`409`/`413`/`503` are not implemented.

**Request correlation (Section 31).** `X-Request-ID` support does not exist. No request
identifier is generated, logged, or returned.

**Retention table (Section 34).** The implemented table is `audit_event_archive` with columns
`event_id`, `archived_at`, `policy_cutoff` — not `audit_retention_status` with an `event_id`,
`archived_at`, `reason` shape.

**Sensitive-value commitment (Section 37).** The implemented formula is
`HMAC-SHA-256(commitmentKey, redactionId + eventId + fieldPath + value)` — it additionally binds
the random per-redaction ID, not just `eventId + jsonPath + sensitiveValue`, so that two fields
holding an identical secret never produce an identical commitment.

**Redaction audit trail (Section 39).** Not implemented. Redacting a field does not write a new
`AUDIT_FIELD_REDACTED` audit event; `RedactionService.redact()` only updates
`audit_redaction_value`.

**Database technology / Testcontainers (Section 25).** Integration tests do not use
Testcontainers. The suite is Mockito-based; the one `@SpringBootTest`
(`AuditLogServiceApplicationTests`) runs against whatever Postgres the local Docker Compose stack
provides.

**Scenario A concurrency test (Section 24).** Not implemented. No test submits concurrent writes
and checks for chain forks; the row-locking design is reviewed by inspection only.

**Scenario A tamper test (Section 22).** Not implemented as an automated integration test that
bypasses the application to edit `audit_event` directly. Tamper-detection logic is covered by
unit tests that hand-construct a mismatched record through a mock repository, which verifies the
detection logic but not a real out-of-band database edit.

**Authentication and authorization.** Not mentioned in this document's original scope, but worth
recording here: no endpoint has any authentication or authorization control, including the
irreversible redaction endpoint and the bulk export endpoint. See
[Threat Model: Known Open Gaps](threat-model.md#known-open-gaps-scenario-b).

See [docs/scenario-a.md](scenario-a.md), [docs/scenario-b.md](scenario-b.md), and
[docs/testing.md](testing.md#known-gaps) for further detail on each of these.

---

# 2. Engineering Principles

The following principles will guide the implementation.

## 2.1 Audit records are immutable

Once an audit event has been written successfully, the application will not provide an API to update or delete that event.

Any operational information that may change later, such as archival state or redaction status, should be stored separately from the immutable event whenever practical.

## 2.2 Tampering must be detectable

The application cannot assume that database access is limited to the application itself.

A record may be changed directly in PostgreSQL by an administrator, script, compromised credential, or operational mistake.

The hash chain must detect this type of change during verification.

## 2.3 Database writes must be atomic

Writing the event and advancing the chain head must happen in the same database transaction.

The system must never commit an audit event while leaving the chain head unchanged, or advance the chain head without successfully storing the corresponding event.

## 2.4 Ordering must be deterministic

Hash chaining requires a clear and stable event order.

The system will use a database-generated sequence value as the authoritative chain ordering mechanism.

The business event timestamp will not be used to determine chain position.

## 2.5 The service owns the audit timestamp

The authoritative audit timestamp will be assigned by the server.

Clients will not control the timestamp used for ordering or audit integrity.

If the system later needs to capture the time when an event occurred in another system, that value can be introduced separately as an `occurredAt` field.

The audit service timestamp will represent when the event was accepted and recorded.

## 2.6 Verification must provide useful diagnostics

The verification endpoint should not return only a generic valid or invalid result.

When verification fails, the service should identify the first record where the problem was detected and classify the type of failure.

This will make operational investigation easier and will also make the integrity behavior easier to test.

---

# 3. Scenario A

## 3.1 Write Audit Event

The service will expose an API that accepts a new audit event.

Initial endpoint:

```text
POST /api/v1/audit/events
```

A request must contain the following fields.

```text
eventType
actorId
resourceType
resourceId
payload
```

Example request:

```json
{
  "eventType": "RECORD_UPDATED",
  "actorId": "employee-123",
  "resourceType": "CLIENT_ACCOUNT",
  "resourceId": "account-456",
  "payload": {
    "field": "address",
    "source": "CSR_PORTAL"
  }
}
```

The application will generate the following values.

```text
eventId
sequenceId
timestamp
contentHash
previousHash
chainHash
```

The server-generated timestamp will use UTC.

Java `Instant` will be used internally.

---

# 4. Event Validation

The service must reject malformed audit events before attempting to write them.

The following rules will apply.

`eventType` is required and must not be blank.

`actorId` is required and must not be blank.

`resourceType` is required and must not be blank.

`resourceId` is required and must not be blank.

`payload` is required.

String fields will have reasonable maximum lengths to prevent accidental or abusive requests.

The initial limits will be:

```text
eventType: 100 characters
actorId: 200 characters
resourceType: 100 characters
resourceId: 200 characters
```

The payload will also have a configured maximum request size.

The initial target is 64 KB.

Large documents should not be stored directly in the audit log. The event should contain identifiers or metadata referencing those documents instead.

---

# 5. Event Identifier

Each audit event will have a UUID generated by the application.

Example:

```text
5c00f45b-20c5-4733-946d-75546f61d3a8
```

The UUID is the external identifier used by the API.

The database sequence will be used internally for ordering.

These two identifiers serve different purposes.

The UUID provides a safe external identifier without exposing record counts.

The sequence provides a stable and efficient chain order.

---

# 6. Append-Only Behavior

The service will not expose endpoints such as:

```text
PUT /api/v1/audit/events/{id}
PATCH /api/v1/audit/events/{id}
DELETE /api/v1/audit/events/{id}
```

Only creation and read operations will be available for immutable audit events.

The persistence layer should also be designed so that event updates are not part of the normal application code path.

The implementation will prefer explicit insert statements instead of treating audit events as general mutable entities.

---

# 7. Hash Chain Design

Each record will store three integrity values.

```text
contentHash
previousHash
chainHash
```

These values serve different purposes.

`contentHash` proves the event content has not changed.

`previousHash` links the record to the previous record.

`chainHash` represents the final integrity value for the current position in the chain.

The chain will be global for the first version of the service.

This means every audit event belongs to one ordered sequence.

---

# 8. Content Hash

The content hash will be calculated from the immutable event fields.

The fields included in the hash will be:

```text
eventId
eventType
actorId
resourceType
resourceId
payload
eventTimestamp
schemaVersion
```

Operational metadata that is allowed to change later must not be included unless the system intends that change to invalidate the record.

The initial hashing algorithm will be SHA-256.

Conceptually:

```text
contentHash =
SHA256(canonicalEventContent)
```

The hash will be stored as a hexadecimal string.

---

# 9. Canonical Event Representation

The service cannot hash arbitrary JSON serialization directly because JSON object property order is not guaranteed to be meaningful.

For example, these payloads should represent the same content:

```json
{
  "name": "John",
  "age": 35
}
```

and:

```json
{
  "age": 35,
  "name": "John"
}
```

The service will create a deterministic representation before hashing.

The canonicalization logic must:

```text
sort object keys consistently
preserve array order
use UTF-8 encoding
use a consistent timestamp format
avoid insignificant whitespace differences
handle nested objects recursively
```

The prototype will implement deterministic JSON serialization using Jackson.

A production version should consider using a formally defined JSON canonicalization standard such as RFC 8785.

This will be documented as a known design limitation of the prototype.

---

# 10. Genesis Hash

The first record in the chain has no previous audit event.

Instead of using `null`, the implementation will use a deterministic genesis value.

The application will calculate:

```text
SHA256("AUDIT_CHAIN_GENESIS_V1")
```

This value becomes the `previousHash` for the first record.

Using a defined genesis value makes verification behavior explicit and avoids special handling around nullable hashes.

---

# 11. Chain Hash

The chain hash will be calculated using the previous chain hash and the current content hash.

Conceptually:

```text
chainHash =
SHA256(previousHash + contentHash)
```

For the first record:

```text
previousHash = GENESIS_HASH
```

For every later record:

```text
previousHash = previous record's chainHash
```

Example:

```text
Event 1
previousHash = GENESIS_HASH
contentHash = A
chainHash = X

Event 2
previousHash = X
contentHash = B
chainHash = Y

Event 3
previousHash = Y
contentHash = C
chainHash = Z
```

If Event 2 is changed directly in the database, its recalculated content hash will no longer match the stored value.

If links between records are changed, the previous hash or chain hash checks will fail.

---

# 12. Chain Head

The service will maintain a separate chain head record.

The chain head represents the latest committed state of the global chain.

It will contain:

```text
chainId
lastSequenceId
lastEventId
lastChainHash
eventCount
```

The initial chain ID will be:

```text
GLOBAL
```

The chain head is necessary for more than write coordination.

It also helps detect removal of the final event.

Without a stored chain head, deleting the last record could leave the remaining records internally consistent.

For example:

```text
Event 1 -> Event 2 -> Event 3
```

If Event 3 is deleted, Events 1 and 2 still form a valid chain.

The chain head allows verification to determine that the stored final hash and event count no longer match the expected state.

---

# 13. Concurrent Writes

The service must handle multiple audit requests arriving at the same time.

A simple in-memory Java lock will not be used because that approach works only when a single application instance is running.

If two application instances were deployed, each instance would have its own lock.

The application will instead coordinate writes through PostgreSQL.

The write transaction will lock the global chain head row using:

```sql
SELECT *
FROM audit_chain_head
WHERE chain_id = 'GLOBAL'
FOR UPDATE;
```

The write flow will be:

```text
begin transaction

lock chain head

read current lastChainHash

generate event ID

assign server timestamp

canonicalize event content

calculate contentHash

use lastChainHash as previousHash

calculate chainHash

insert audit event

update chain head

commit transaction
```

This guarantees that two successful writes cannot use the same chain position.

---

# 14. Transaction Requirements

Event insertion and chain head advancement must occur within the same transaction.

If event insertion fails, the chain head must not advance.

If chain head update fails, the event insert must be rolled back.

If the process crashes during the transaction, PostgreSQL transaction handling must leave the database in the previous committed state.

The application service responsible for the write operation will use Spring transaction management.

---

# 15. Query API

The service will expose:

```text
GET /api/v1/audit/events
```

The endpoint will support filtering by any combination of:

```text
actorId
resourceType
resourceId
eventType
from
to
```

Example:

```text
GET /api/v1/audit/events?actorId=employee-123
```

Another example:

```text
GET /api/v1/audit/events?resourceType=CLIENT_ACCOUNT&resourceId=account-456
```

Another example:

```text
GET /api/v1/audit/events?eventType=USER_LOGIN&from=2026-09-01T00:00:00Z&to=2026-10-01T00:00:00Z
```

When multiple filters are supplied, they will be combined using AND semantics.

---

# 16. Time Range Semantics

Time ranges will use the following behavior:

```text
from is inclusive
to is exclusive
```

This can be represented as:

```text
[from, to)
```

For example:

```text
from=2026-09-01T00:00:00Z
to=2026-10-01T00:00:00Z
```

means every matching record recorded during September.

If `from` is after `to`, the request will be rejected with HTTP 400.

---

# 17. Pagination

The API will use cursor-based pagination instead of page-number and offset pagination.

The database sequence is a good cursor because audit events are append-only and sequence values increase monotonically.

Example request:

```text
GET /api/v1/audit/events?limit=100
```

Example response:

```json
{
  "items": [],
  "nextCursor": "12345",
  "hasMore": true
}
```

The next request can use:

```text
GET /api/v1/audit/events?limit=100&cursor=12345
```

The database query will conceptually use:

```sql
WHERE sequence_id > :cursor
ORDER BY sequence_id ASC
LIMIT :limit
```

The default limit will be 100.

The maximum limit will be 500.

Invalid limits and invalid cursors will be rejected.

Cursor pagination is preferred because large SQL offsets become increasingly expensive and can also behave poorly while new records are being inserted.

---

# 18. Chain Verification API

The service will expose:

```text
GET /api/v1/audit/verify
```

The endpoint will verify the complete stored chain.

A successful response will include:

```json
{
  "intact": true,
  "recordsVerified": 100,
  "firstInconsistency": null
}
```

If verification fails, the response will identify the first record where an inconsistency was detected.

Example:

```json
{
  "intact": false,
  "recordsVerified": 41,
  "firstInconsistency": {
    "sequence": 42,
    "eventId": "5c00f45b-20c5-4733-946d-75546f61d3a8",
    "violationType": "CONTENT_HASH_MISMATCH"
  }
}
```

---

# 19. Chain Verification Logic

Verification will start with:

```text
expectedPreviousHash = GENESIS_HASH
```

Records will be read in ascending `sequence_id` order.

For each record, the verifier will perform the following checks.

First, reconstruct the canonical event representation.

Second, calculate the content hash again.

```text
recomputedContentHash =
SHA256(canonicalEventContent)
```

Third, compare the recalculated content hash with the stored `contentHash`.

If they differ, verification fails with:

```text
CONTENT_HASH_MISMATCH
```

Fourth, compare the record's `previousHash` with the expected previous chain hash.

If they differ, verification fails with:

```text
PREVIOUS_HASH_MISMATCH
```

Fifth, calculate the chain hash again.

```text
recomputedChainHash =
SHA256(previousHash + contentHash)
```

Sixth, compare the result with the stored `chainHash`.

If they differ, verification fails with:

```text
CHAIN_HASH_MISMATCH
```

After a record passes verification:

```text
expectedPreviousHash = record.chainHash
```

After all records have been processed, the result will be compared with the stored chain head.

---

# 20. Verification Failure Types

The first implementation will support the following failure types:

```text
CONTENT_HASH_MISMATCH

PREVIOUS_HASH_MISMATCH

CHAIN_HASH_MISMATCH

CHAIN_HEAD_MISMATCH

CHAIN_COUNT_MISMATCH

INVALID_GENESIS_REFERENCE
```

Additional failure types can be added later if needed.

The endpoint will stop at the first inconsistency because the assignment asks for the first broken record, and later errors may only be consequences of the initial corruption.

---

# 21. Verification Consistency During Active Writes

Verification may run while new audit events are being written.

The verifier must not read one version of the event table and a newer version of the chain head, because that could create a false verification failure.

The verification process will use a consistent database snapshot.

The preferred implementation is a read-only transaction using PostgreSQL `REPEATABLE READ`.

The chain head and event records will therefore represent the same committed database state during verification.

---

# 22. Scenario A Tamper Test

The Scenario A implementation is not considered complete until direct database tampering has been demonstrated.

The test flow will be:

```text
create Event 1

create Event 2

create Event 3

run chain verification

confirm intact = true
```

Then modify one event directly in PostgreSQL.

Example:

```sql
UPDATE audit_event
SET actor_id = 'modified-directly'
WHERE sequence_id = 2;
```

Run verification again.

Expected result:

```text
intact = false
violationType = CONTENT_HASH_MISMATCH
sequence = 2
```

The application must not be involved in the modification.

The purpose of the test is to prove that direct datastore tampering is detectable.

---

# 23. Additional Tamper Cases

Scenario A testing should also cover the following cases.

Modify the payload directly.

Expected result:

```text
CONTENT_HASH_MISMATCH
```

Modify the actor ID directly.

Expected result:

```text
CONTENT_HASH_MISMATCH
```

Modify the stored content hash.

Expected result:

```text
CONTENT_HASH_MISMATCH
```

Modify the previous hash.

Expected result:

```text
PREVIOUS_HASH_MISMATCH
```

Modify the chain hash.

Expected result:

```text
CHAIN_HASH_MISMATCH
```

Delete a middle event.

Expected result:

The following record should fail its previous hash validation.

Delete the last event.

Expected result:

The stored chain head should no longer match the last available event.

Delete all events while leaving a non-empty chain head.

Expected result:

The event count or chain head validation should fail.

---

# 24. Scenario A Concurrency Test

The implementation must include a concurrent write test.

The test will submit multiple audit events in parallel.

The initial target will be 100 concurrent requests.

After all requests complete, the test will verify:

```text
100 events were stored

all sequence values are unique

all chain hashes are unique

no chain forks exist

the chain head event count is correct

GET /api/v1/audit/verify reports intact = true
```

This test validates the database locking strategy and transaction boundary.

---

# 25. Database Technology

PostgreSQL will be used as the system of record.

The prototype will run PostgreSQL 16 locally through Docker Compose.

Flyway will manage schema migrations.

Integration tests will use PostgreSQL through Testcontainers rather than an in-memory replacement database.

This is intentional because the implementation depends on PostgreSQL behavior including:

```text
JSONB

SELECT FOR UPDATE

transaction isolation

locking

database sequences
```

Using a different database in integration tests could hide database-specific issues.

---

# 26. Persistence Approach

The initial implementation will use Spring JDBC.

A rich mutable JPA model is not necessary for this service.

Audit events have a simple lifecycle:

```text
validate

calculate integrity metadata

insert

read
```

The application should not treat audit events as normal mutable entities.

Explicit SQL also makes locking and append behavior easier to understand during review.

This decision can be revisited if future requirements justify a different persistence model.

---

# 27. Initial Database Tables

Scenario A requires two main tables.

The first is:

```text
audit_event
```

This contains immutable audit events and their integrity metadata.

The second is:

```text
audit_chain_head
```

This contains the current committed chain state and coordinates writers.

Initial `audit_event` fields:

```text
sequence_id
event_id
event_type
actor_id
resource_type
resource_id
integrity_payload
event_timestamp
schema_version
content_hash
previous_hash
chain_hash
created_at
```

Initial `audit_chain_head` fields:

```text
chain_id
last_sequence_id
last_event_id
last_chain_hash
event_count
```

---

# 28. Initial Indexes

The following indexes will be added based on required query patterns.

```text
actor_id

event_type

resource_type, resource_id

event_timestamp

actor_id, event_timestamp

resource_id, event_timestamp
```

Indexes will be validated against actual query behavior later.

The goal is not to add every possible index in advance.

---

# 29. HTTP Error Handling

The API will return a consistent error response.

Example:

```json
{
  "code": "INVALID_TIME_RANGE",
  "message": "from must be earlier than to",
  "requestId": "75cd1f76-7d96-4318-a30e-1457efcc4faa",
  "timestamp": "2026-09-17T01:00:00Z"
}
```

The initial HTTP status behavior will be:

```text
400 Bad Request
Invalid input or malformed filter

404 Not Found
Requested audit resource does not exist

409 Conflict
Request conflicts with an existing operation, such as an idempotency key reused with different content

413 Payload Too Large
Payload exceeds the configured request limit

500 Internal Server Error
Unexpected server failure

503 Service Unavailable
Required infrastructure such as PostgreSQL is unavailable
```

Internal stack traces will not be returned to clients.

---

# 30. Logging

Application logs must not contain sensitive audit payload data.

Structured logs should include operational information such as:

```text
requestId
route
HTTP status
duration
eventId when appropriate
sequenceId when appropriate
error classification
```

Payload contents, secrets, encryption keys, and sensitive personal information must not be logged.

---

# 31. Request Correlation

Each HTTP request should have a request identifier.

The service will support:

```text
X-Request-ID
```

If the caller does not provide one, the application will generate a UUID.

The request ID will be included in structured logs and API error responses.

This will make debugging easier without exposing audit payloads.

---

# 32. Health Check

Spring Boot Actuator will expose:

```text
GET /actuator/health
```

The endpoint will be used during local setup and demonstrations to confirm that the service is running and the required dependencies are available.

---

# 33. Scenario B Scope

Scenario B will be implemented after Scenario A is complete and verified.

It introduces three capabilities:

```text
retention

structured redaction

bulk export
```

These features must not weaken Scenario A integrity guarantees.

---

# 34. Retention

Audit records older than a configurable retention window must be archivable.

The first implementation will use soft archival rather than physical deletion.

Archive state should preferably be stored separately from the immutable event record.

A possible table is:

```text
audit_retention_status
```

with fields such as:

```text
event_id
archived_at
reason
```

This allows `audit_event` itself to remain insert-only.

Chain verification will continue to include archived records.

Archiving a record must not create an integrity failure.

The retention period will be configurable.

No legal or regulatory retention duration will be assumed unless one is provided by the business or compliance team.

---

# 35. Structured Redaction

Some payload fields may contain information that must later be removed for privacy reasons.

The system must support this without changing the immutable hash chain.

The design will separate the cryptographic representation of a sensitive value from the retrievable plaintext value.

Fields that may require future redaction must be identified when the event is written.

The immutable audit payload will contain a cryptographic commitment instead of the plaintext sensitive value.

The actual value will be stored separately in encrypted form.

A later redaction operation can destroy or remove the retrievable encrypted value while leaving the original commitment unchanged.

Because the immutable audit content does not change, the hash chain remains valid.

---

# 36. Redaction Scope Limitation

The prototype will only guarantee safe redaction for fields that were classified as redactable when the event was originally written.

A normal field that has already been stored and hashed as plaintext cannot later be removed from the immutable audit record without changing its original cryptographic commitment.

This limitation will be documented clearly.

Supporting arbitrary retroactive redaction would require a different event representation from the beginning.

---

# 37. Sensitive Value Commitment

Plain SHA-256 will not be used directly for low-entropy sensitive values such as account numbers.

Values from a predictable domain may be vulnerable to guessing if their plain hash is available.

The prototype will use HMAC-SHA-256 for sensitive-value commitments.

Conceptually:

```text
commitment =
HMAC-SHA-256(
    commitmentKey,
    eventId + jsonPath + sensitiveValue
)
```

The commitment key will be provided through configuration or an environment secret and will not be stored in source control.

---

# 38. Sensitive Value Encryption

Sensitive values that still need to be readable will be encrypted separately.

The prototype will use AES-256-GCM.

Production key management would normally use a managed key service and envelope encryption.

For the prototype, encryption keys will be supplied through environment configuration.

Keys will not be committed to Git.

---

# 39. Redaction Audit Trail

A redaction is itself a security-relevant event.

When a field is redacted, the service should write a new audit event describing the action.

Example event type:

```text
AUDIT_FIELD_REDACTED
```

The event can include:

```text
targetEventId
fieldPath
reason
requestedBy
```

It must not include the sensitive value that was removed.

This preserves an audit trail of the privacy action without changing the original event.

---

# 40. Bulk Export

The service will support exporting audit records for:

```text
actorId
```

or:

```text
resourceId
```

The exported records must form a self-contained verifiable bundle.

The bundle should include:

```text
bundle version

export timestamp

filter criteria

records

record integrity metadata

record count

bundle hash

digital signature
```

The prototype will use Ed25519 to sign the bundle manifest.

The corresponding public key will allow an external recipient to verify that the exported data has not changed since export.

A plain SHA-256 checksum alone is not sufficient because anyone modifying the bundle could also calculate a new checksum.

---

# 41. Export Integrity Boundary

A filtered export contains only a subset of the global chain.

The exported bundle therefore cannot independently reconstruct every omitted global chain record.

The bundle must clearly define what it proves.

The signed bundle will prove that the exported records and metadata have not changed after export.

Where practical, records will retain their original content hash, previous hash, chain hash, and sequence information.

The limitation that a partial export does not contain the full global chain will be documented.

---

# 42. Scenario C Requirement

The original product statement is:

```text
Regulators need to be able to audit access to client account data.
```

This statement is not specific enough to implement directly.

Before writing code, the engineering team would normally clarify:

```text
what qualifies as access

whether denied attempts are included

which actors must be captured

which applications are in scope

what account identifier may safely be stored

whether access purpose is required

which data categories were viewed

what report fields regulators require

what retention period applies

who is authorized to run the report

whether export is required

whether reports must cover multiple systems
```

For the prototype, assumptions will be documented explicitly.

---

# 43. Scenario C Working Requirement

The working requirement for the prototype will be:

The audit service will record successful and denied attempts to access client account data.

Each access event will identify the actor, the affected account using an internal opaque account identifier, the action performed, the result of the request, the source application, the request or correlation identifier, and the categories of account data involved where available.

The service will support querying these records by account, actor, result, and time range.

The service will not store actual client account data inside the compliance event.

---

# 44. Example Compliance Event

Example:

```json
{
  "eventType": "CLIENT_ACCOUNT_DATA_ACCESSED",
  "actorId": "employee-983",
  "resourceType": "CLIENT_ACCOUNT",
  "resourceId": "acct-internal-2383",
  "payload": {
    "action": "READ",
    "outcome": "SUCCESS",
    "purpose": "CUSTOMER_SUPPORT",
    "sourceApplication": "CSR_PORTAL",
    "requestId": "req-23981",
    "sessionId": "sess-481",
    "dataCategories": [
      "PROFILE",
      "TRANSACTIONS"
    ]
  }
}
```

A denied request may use:

```text
CLIENT_ACCOUNT_DATA_ACCESS_DENIED
```

with a payload containing the denial reason.

---

# 45. Scenario C Scope Boundary

The prototype will not attempt to implement the following areas unless specifically required later:

```text
a regulator-facing user interface

legal interpretation of retention periods

enterprise identity-provider integration

full authorization administration

regulator-specific document templates

multi-region data residency controls

cross-system audit ingestion

SIEM integration

production KMS integration

enterprise approval workflows
```

These areas are outside the minimum scope required to demonstrate the audit platform design.

---

# 46. Testing Expectations

The implementation will include unit and integration tests.

The important test categories are:

```text
input validation

hash calculation

canonical JSON

genesis behavior

event creation

chain linking

database transactions

query filters

time boundaries

cursor pagination

full-chain verification

direct datastore tampering

record deletion

concurrent writers

retention

redaction

encrypted-value handling

export signing

export verification

compliance reporting
```

Database integration tests will use Testcontainers with PostgreSQL.

---

# 47. Scenario A Definition of Done

Scenario A is complete only when all of the following are true.

The application starts successfully.

PostgreSQL schema creation succeeds through Flyway.

An event can be written through the REST API.

A second event correctly references the first event's chain hash.

Multiple events can be queried using the required filters.

Pagination works without duplicate or missing events.

The full chain verification endpoint reports an intact chain.

Direct modification of an event causes verification failure.

Direct deletion of a middle event causes verification failure.

Deletion of the final event is detected using the stored chain head.

Concurrent writes do not produce chain forks.

Integration tests pass using PostgreSQL Testcontainers.

The Scenario A design and trade-offs are documented.

---

# 48. Scenario B Definition of Done

Scenario B is complete when:

Retention can archive eligible records without modifying immutable event content.

Verification continues to succeed when records are legitimately archived.

A configured sensitive field can be redacted without changing the original chain hash.

The redaction action is itself audited.

The redacted value is no longer returned through normal queries.

Bulk export works for actor ID and resource ID.

An exported bundle can be verified independently.

Modification of an exported bundle causes signature verification to fail.

Trade-offs and limitations are documented.

---

# 49. Scenario C Definition of Done

Scenario C is complete when:

The ambiguous product requirement is documented.

Open questions are identified.

Implementation assumptions are written down.

A normalized technical requirement is defined.

A compliance access event format is documented.

The implementation supports the selected compliance reporting use case.

Items intentionally left out of scope are listed with reasons.

---

# 50. Known Initial Limitations

The first version will have the following known limitations.

The system uses one global chain, which serializes writes around a shared chain head.

Full verification requires reading the complete chain.

JSON canonicalization will use application-controlled deterministic serialization rather than a complete standards-based implementation.

The database remains a trusted operational dependency.

An attacker with unrestricted database access who rewrites all records, recalculates all hashes, and also replaces the chain head could create a new internally consistent history.

A production system should periodically anchor signed chain checkpoints outside the primary database.

Redaction must be planned when sensitive fields are ingested.

The initial deployment is a single-region prototype.

These limitations are accepted for the assignment because the implementation is intended to demonstrate sound integrity design while remaining small enough to build, test, and explain thoroughly.

---

# 51. Production Follow-Up

If the prototype were taken toward production, the next areas to evaluate would include:

```text
external signed chain checkpoints

WORM or immutable storage

partitioned hash chains for higher write throughput

managed key storage

key rotation

authorization and authentication

rate limiting

database privilege separation

incremental verification

scheduled integrity checks

high availability

backup integrity

restore testing

security monitoring

alerting on verification failures

data retention governance

cross-region requirements
```

These are future engineering considerations and are not required before completing the core assignment.
