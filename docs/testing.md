# Testing Strategy

## Objective

Testing focuses on correctness, integrity, failure handling, and the scenarios described in the
assignment.

## Actual approach

The suite is almost entirely Mockito-based unit tests — repositories are tested against a mocked
`JdbcTemplate` (asserting the SQL and bound arguments built), and services are tested against
mocked collaborators. `AuditLogServiceApplicationTests` is the one `@SpringBootTest`, and it runs
against the local Docker Compose Postgres instance directly rather than an isolated Testcontainers
database (Testcontainers was originally planned — see [Known Gaps](#known-gaps) — but was never
adopted).

62 tests pass with the Scenario C implementation (`./mvnw test`).

## Unit Tests

Input validation (`CreateAuditEventRequestTest`)

SHA-256 hashing, genesis hash, chain-hash linkage (`HashServiceTest`)

Canonical JSON behavior (`CanonicalJsonServiceTest`)

Chain-head locking and advancement, including row-count-mismatch failure (`ChainHeadRepositoryTest`)

Event insert, filtered/paginated query building, row mapping (`AuditEventRepositoryTest`)

Transactional event creation, chaining off the current tip rather than genesis (`AuditCommandServiceTest`)

Controller request/response mapping and limit clamping (`AuditControllerTest`)

Full-chain verification success and first-inconsistency detection (`ChainVerificationServiceTest`)

## Scenario B Tests

Retention: cutoff boundary (strict `<`), empty results, repeated archival, idempotency SQL shape
(`RetentionServiceTest`)

Normal queries excluding archived events; verification and export including them
(`AuditEventRepositoryTest`)

Redaction: nested field redaction, invalid/duplicate paths, commitment domain separation, safe
handling of a forged marker (`RedactionServiceTest`)

Redaction + verification: chain stays intact both before and after redaction, repeated redaction
is idempotent (`RedactionVerificationEndToEndTest`)

Export: filtering by actor/resource, both/neither filter rejection, archived-record inclusion,
chain-anchor content, independent digest recomputation, tamper detection (`ExportServiceTest`)

## Scenario C Tests

Client account access request validation: required fields, enum decisions, category validation, and optional
category handling (`ClientAccountAccessRequestTest`)

Scenario C mapping and query boundary: fixed event/resource types, exact bounded payload, compliance query
filters, page size cap, and invalid time range rejection (`ClientAccountAccessServiceTest`)

Controller behavior: created response and query response mapping (`ClientAccountAccessControllerTest`)

## Known Gaps

The following items are called out in [Scenario A](scenario-a.md#definition-of-done--actual-status)
and the original requirements as intended coverage, but do not currently exist:

- **Direct-tampering integration test.** No test modifies `audit_event` directly in the database
  (bypassing the application) and confirms `GET /api/v1/audit/verify` detects it. This is
  currently verified only by code review of `ChainVerificationService`, plus unit tests that hand-
  construct a mismatched `AuditEvent` in a mock — which proves the detection *logic* but not that
  a real out-of-band database edit is actually caught end to end.
- **Concurrent-writer test.** No test submits multiple simultaneous write requests and confirms no
  chain forks, unique sequence/chain hashes, and a correct final chain-head count. The row-locking
  design (`SELECT ... FOR UPDATE` in `ChainHeadRepository.lockOrCreate`) is reviewed by inspection
  only.
- **Testcontainers-based integration tests.** The original plan was to run integration tests
  against PostgreSQL via Testcontainers specifically to exercise JSONB, `SELECT FOR UPDATE`,
  isolation levels, and sequences without relying on the local Docker Compose instance being up.
  That was never implemented; the suite is unit/mock-based instead, with one `@SpringBootTest`
  smoke test against whatever Postgres is running locally.
- **Redaction audit trail.** The original design called for redaction to itself write a new audit
  event (e.g. `AUDIT_FIELD_REDACTED`). `RedactionService.redact()` does not do this — it only
  updates `audit_redaction_value`, so there is currently no test for it either.
- **HTTP-level validation tests.** No `@WebMvcTest`/`MockMvc` test exercises request validation
  (e.g. a malformed `POST` body) through the real Spring MVC dispatch path; validation logic is
  only tested by calling controller methods directly, which bypasses `@Valid` enforcement.
