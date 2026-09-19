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
See [Scenario B](docs/scenario-b.md) for the security model and its limitations.

## Planned Technology

Java 21

Spring Boot

PostgreSQL

Spring JDBC

Flyway

Docker Compose

JUnit

Testcontainers

OpenAPI

Spring Boot Actuator

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

Build a runnable jar:

```bash
./mvnw clean package
```

Stop PostgreSQL:

```bash
docker compose down
```

## Repository Structure

The repository contains the application code, architecture documentation, test strategy, scenario documentation, engineering decisions, AI usage traceability, and final implementation summary.

Additional setup and execution instructions will be added as implementation progresses.
