# Tamper-Evident Audit Log Service

This project implements a tamper-evident audit log service using Java, Spring Boot, and PostgreSQL.

The system is designed to provide an append-only audit history where unauthorized changes to previously stored records can be detected through hash-chain verification.

The implementation is being developed in three stages.

Scenario A covers the core audit event write, query, pagination, hash-chain, and verification capabilities.

Scenario B extends the service with retention, structured redaction, and verifiable export.

Scenario C applies the audit platform to a compliance reporting requirement around access to client account data.

## Current Status

The project is currently in the design and initial implementation phase.

Requirements and initial architecture decisions have been documented before application code is introduced.

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
