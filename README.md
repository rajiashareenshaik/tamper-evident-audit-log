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

Originally planned but not adopted: Testcontainers (tests use Mockito for units and the local
Docker Compose Postgres directly for the one `@SpringBootTest` context-load test, not an isolated
Testcontainers instance) and OpenAPI (no springdoc/swagger dependency exists; the API is
documented in [docs/requirements.md](docs/requirements.md) and [docs/scenario-b.md](docs/scenario-b.md) instead).

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
