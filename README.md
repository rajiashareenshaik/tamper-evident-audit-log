# Tamper-Evident Audit Log Service

This project implements a tamper-evident audit log service using Java, Spring Boot, and PostgreSQL.

The system is designed to provide an append-only audit history where unauthorized changes to previously stored records can be detected through hash-chain verification.

The implementation is being developed in three stages.

Scenario A covers the core audit event write, query, pagination, hash-chain, and verification capabilities.

Scenario B extends the service with retention, structured redaction, and verifiable export.

Scenario C applies the audit platform to a compliance reporting requirement around access to client account data.

## Current Status

Scenarios A and B are implemented. The service supports event writes and filtered queries, full-chain
verification, policy-based soft archiving, commitment-backed structured redaction, and signed bulk exports.
Scenario C is documented as a well-reasoned partial implementation: the platform can store and report
submitted access events through a dedicated typed API, but trusted capture, authorization, and tenant
isolation are not implemented. See [Scenario C](docs/scenario-c.md) for the scope boundary.

## Technology

In use: Java 21, Spring Boot, PostgreSQL, Spring JDBC, Flyway, Docker Compose, JUnit 5, Mockito,
Spring Boot Actuator.

Unit tests use Mockito. The HTTP integration suite starts the application and uses a separate
PostgreSQL schema for each run. Run it against a disposable database:

```bash
AUDIT_E2E_DB_URL=jdbc:postgresql://127.0.0.1:55432/auditdb ./mvnw -Dtest=AuditHttpIT test
```

Database credentials default to `audit` / `audit`; override them with `AUDIT_E2E_DB_USER`
and `AUDIT_E2E_DB_PASSWORD`.

## High traffic

Writes share one global chain lock. This update shortens the work under that lock, bounds
concurrent HTTP writes, and returns `503` when write capacity is exhausted. It also adds
PostgreSQL integration tests and a repeatable load script:

```bash
python3 scripts/load-test.py --requests 2000 --concurrency 8
```

The script writes real records. Use a separate test environment. Sustained TPS has not been
measured; batching and separate chains remain future work.

## Running Locally

Start PostgreSQL (exposed on `localhost:55432`, database `auditdb`):

```bash
docker compose up -d
```

Run the application (applies Flyway migrations on startup, listens on `localhost:8080`):

```bash
./mvnw spring-boot:run
```

Run the test suite:

```bash
./mvnw test
```

For local development, set `AUDIT_CRYPTOGRAPHIC_KEY` to a private value. The fallback in
`application.yml` is intentionally limited to local use.

## Scenario B endpoints

```text
POST /api/v1/audit/retention/archive-expired
POST /api/v1/audit/events/{eventId}/redactions
GET  /api/v1/audit/export?actorId=...
GET  /api/v1/audit/export?resourceId=...
GET  /api/v1/audit/verify
```

## Scenario C endpoints

```text
POST /api/v1/audit/client-account-access
GET  /api/v1/audit/client-account-access?actorId=...&accountId=...&from=...&to=...
```

The write endpoint accepts the fixed client account access contract documented in
[Scenario C](docs/scenario-c.md). The read endpoint returns only client account access events and
supports actor, account, time, sequence cursor, and page size filters. These endpoints have no
authentication or tenant isolation in the prototype.

Build a runnable jar:

```bash
./mvnw clean package
```

Stop PostgreSQL:

```bash
docker compose down
```

## Repository Structure

The repository contains the application code, [architecture documentation](docs/architecture.md),
[test strategy](docs/testing.md), scenario documentation ([A](docs/scenario-a.md),
[B](docs/scenario-b.md), [C](docs/scenario-c.md)), [engineering decisions](docs/decisions/),
[threat model](docs/threat-model.md), [AI usage traceability](AI_USAGE.md), and final
implementation summary (`FINAL_ENGINEERING_SUMMARY.md`, written once Scenario C is complete).

## Additional integration checks

`AuditHttpIT` also checks retention at the exact cutoff, export signatures from the HTTP response,
verification and exports during writes, two application instances sharing one chain, pool
exhaustion, burst rejection and recovery, and a 128 KiB payload round trip.

The optional soak test reconciles successful HTTP responses with stored events and verifies the
chain afterward. Run it only against a disposable database:

```bash
AUDIT_E2E_DB_URL=jdbc:postgresql://127.0.0.1:55432/auditdb \
AUDIT_RUN_SOAK=true AUDIT_SOAK_SECONDS=60 \
./mvnw -Dtest=AuditHttpIT test
```

The soak uses twelve clients at fixed concurrency. It checks correctness under sustained writes;
it does not certify a target TPS or latency SLA. Database failover, process crashes, ambiguous
commit outcomes, and ingress payload rejection still need separate tests. Authentication and
idempotency tests depend on those features being implemented.
