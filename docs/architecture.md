# Architecture

## Overview

The system will initially be implemented as a single Spring Boot service backed by PostgreSQL.

The main goal is to keep the design small enough to reason about thoroughly while still handling the important integrity, concurrency, validation, and auditability concerns required by the assignment.

## Main Components

The service will contain separate responsibilities for:

API handling

Audit event creation

Audit event querying

Hash generation

Canonical JSON generation

Chain-head management

Chain verification

Retention

Redaction

Export

Compliance reporting

## Persistence

PostgreSQL will be the system of record.

Flyway will manage schema changes.

Spring JDBC will be used for explicit SQL and transaction handling.

## Integrity Model

Each audit event will contain:

contentHash

previousHash

chainHash

The service will use one global chain for the prototype.

The chain head will be stored separately and locked during writes to prevent concurrent writers from creating forks.

## Deployment

The initial version will run locally using Docker Compose for PostgreSQL and Spring Boot for the application.

Production concerns such as horizontal scaling, external key management, and external integrity anchoring will be documented separately.
