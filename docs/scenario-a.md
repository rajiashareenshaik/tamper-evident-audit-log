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
| Direct-tampering detection (automated test) | Covered by `AuditHttpIT` with direct SQL mutation and deletion. |
| Concurrent-writer test | Covered by 36 requests from six clients; the original 100-parallel-request target remains a load-test task. |
| PostgreSQL integration tests | Real HTTP and PostgreSQL coverage uses an isolated schema in a supplied database. Testcontainers is not used. |

The README includes the PostgreSQL integration-test command. Multi-instance load testing remains open.
