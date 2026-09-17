# Testing Strategy

## Objective

Testing will focus on correctness, integrity, failure handling, concurrency, and the scenarios described in the assignment.

## Unit Tests

Input validation

SHA-256 hashing

Canonical JSON behavior

Genesis hash behavior

Chain-hash calculation

Time-range validation

Cursor handling

## Integration Tests

PostgreSQL persistence

Flyway migrations

Transactional event creation

Chain-head locking

Query filters

Cursor pagination

Full-chain verification

## Tamper Tests

Modify payload directly

Modify actor directly

Modify content hash directly

Modify previous hash directly

Modify chain hash directly

Delete a middle event

Delete the last event

Delete all events while retaining a non-empty chain head

## Concurrency Tests

Run multiple event writes in parallel.

Confirm that:

all events are stored

sequence values are unique

chain hashes are unique

no chain fork exists

the chain head count is correct

verification succeeds

## Scenario B Tests

Retention

Redaction

Encrypted value handling

Redaction audit events

Export generation

Export signature verification

Modified export detection

## Test Environment

Integration tests will use PostgreSQL through Testcontainers rather than an in-memory database.
