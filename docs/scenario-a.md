# Scenario A

## Objective

Scenario A establishes the core audit log service. It supports:

Writing audit events

Querying audit events

Filtering

Cursor pagination

Hash chaining

Full-chain verification

Detection of direct datastore tampering (by design — see below on test coverage)

## Status

Implemented. `POST`/`GET /api/v1/audit/events` and `GET /api/v1/audit/verify` are live; see
[docs/architecture.md](architecture.md) for the component diagram and
[ADR-001](decisions/ADR-001-global-hash-chain.md)/[ADR-002](decisions/ADR-002-server-timestamps.md)
for the two core design decisions.

## Definition of Done — actual status

| Item | Status |
|---|---|
| Service writes multiple chained events | Done |
| Events can be queried and filtered | Done — `actorId`, `resourceType`, `resourceId`, `eventType`, `from`, `to`, cursor pagination |
| Full-chain verification | Done — `GET /api/v1/audit/verify`, `ChainVerificationService` |
| Direct-tampering detection (design) | Done — content/previous/chain hash recomputation, chain-head cross-check |
| Direct-tampering detection (automated test) | **Not done** — no test currently modifies `audit_event` directly in the database and re-runs verification; this is currently only exercised manually/by design review |
| Concurrent-writer test (100 parallel requests, no forks) | **Not done** — no such test exists; the row-lock design (`SELECT ... FOR UPDATE` in `ChainHeadRepository.lockOrCreate`) is reviewed by inspection only |
| Integration tests via Testcontainers | **Not done as originally planned** — the test suite is Mockito-based; the one `@SpringBootTest` (`AuditLogServiceApplicationTests`) runs against the local Docker Compose Postgres directly, not an isolated Testcontainers instance |

These three gaps (tamper test, concurrency test, Testcontainers) are the main open items carried
forward from Scenario A's original definition of done — see
[docs/testing.md](testing.md#known-gaps).
