# Final Engineering Summary

## Outcome

This repository contains a working prototype of a tamper evident audit log built with Java 21, Spring Boot, PostgreSQL, Spring JDBC, and Flyway.

Scenario A implements event creation, filtered queries, cursor pagination, a global hash chain, and full chain verification. Scenario B adds soft archival, structured field redaction, and signed export. Scenario C turns an ambiguous compliance statement into a clarified requirement and technical design, then identifies which parts are supported by the existing platform and which parts remain incomplete.

The prototype demonstrates the core design, but it is not ready to serve as a production compliance system. Authentication, authorization, trusted producer identity, reliable capture from client account systems, persistent export keys, and database integration tests are still required.

## Plan and rationale

The work was divided into three stages because later features depend on the integrity model established in Scenario A.

Scenario A first established deterministic ordering, canonical serialization, transactional append behavior, and complete chain verification. These are prerequisites for any later claim about retention, redaction, or export.

Scenario B kept the immutable audit row unchanged. Retention adds separate archive metadata. Redaction replaces selected values with commitments before hashing and stores encrypted recoverable values separately. Export packages matching records with chain metadata and a signed digest.

Scenario C was treated as a requirements exercise before implementation. The original sentence does not define access coverage, identity trust, report authorization, retention, or evidence delivery. The resulting design uses a dedicated typed CLIENT_ACCOUNT_ACCESS endpoint and fixed query. No account application is integrated with the service, so end to end capture remains outside the prototype.

## Main artifacts

The architecture is documented in docs/architecture.md. Detailed requirements and deviations are in docs/requirements.md. Scenario decisions are in docs/scenario-a.md, docs/scenario-b.md, and docs/scenario-c.md. The threat model is in docs/threat-model.md. Testing and known gaps are in docs/testing.md. AI assistance and engineering decisions are recorded in AI_USAGE.md.

The application exposes general event creation and query endpoints, Scenario C client account access endpoints, a verification endpoint, a retention endpoint, a redaction endpoint, and an export endpoint. Flyway migrations create the Scenario A and Scenario B tables. Scenario C reuses those tables because it introduces an event contract rather than a new persistence model.

## Key design decisions

The service uses a single global chain. This gives a simple total order and a direct full chain verification model. It also serializes writes on one chain head row and limits throughput.

The server assigns the audit timestamp. This prevents callers from controlling an integrity field, but it records when the service accepted an event rather than when an action occurred in another system.

Event content is converted to canonical JSON before SHA 256 hashing. Each chain hash covers the previous chain hash and the current content hash. Event insertion and chain head advancement happen in one database transaction.

Archived records remain physically present. Normal queries hide them, while verification and export include them. This preserves chain verification but does not reclaim storage.

Redactable values are replaced before hashing with a placeholder and keyed commitment. Their encrypted values are stored in a separate table and can be destroyed later without changing the hashed audit event. This preserves integrity after redaction, but key rotation and ciphertext context binding remain unresolved.

Filtered exports include record hashes, a chain anchor, a bundle digest, a signature, and the public key. Because filtered records may not be adjacent in the global chain, the bundle is not a complete inclusion proof. The signing key is generated at startup, so it is not a stable organizational identity.

## Validation completed

The automated suite covers hashing, canonical JSON, chain head behavior, event creation, query construction, pagination, chain verification, retention, redaction, and export. The Scenario B tests include independent digest recomputation, tamper detection within an export, archive behavior, commitment behavior, and verification before and after redaction.

The test suite is mainly unit tests using mocks. One Spring context test depends on the local PostgreSQL service. Direct database tampering and concurrent writer behavior are not covered by automated PostgreSQL integration tests. Those are important gaps because they exercise the exact database behavior on which the design relies.

## Risks and tradeoffs

There is no authentication or authorization. Any caller that can reach the service can create, query, redact, verify, or export records. Caller supplied actor identity is not trustworthy without producer authentication.

The hash chain detects inconsistencies when verification runs. It does not stop a privileged administrator from rewriting records and rebuilding the chain. External checkpoints or another independently controlled integrity anchor would strengthen the claim.

One global chain is easy to reason about but creates a write bottleneck. A higher throughput design may need partitioned chains with signed checkpoints or another ordered sealing design.

The export is assembled without a documented database snapshot boundary between the records and chain head. A concurrent append may produce a bundle whose selected records and reported head were observed at different moments.

Retention is soft archival rather than deletion. Redaction destroys recoverable sensitive values but retains commitments. Legal suitability depends on the applicable policy and jurisdiction.

Scenario C has the largest functional boundary. The audit service can store submitted access events, but it cannot prove completeness until every approved account access path is integrated with a reliable capture mechanism.

## Assumptions and limitations

PostgreSQL is the system of record and the database transaction and row lock behave as designed. The deployment uses a single logical global chain. The service clock is sufficiently accurate for recording acceptance time. Source applications are responsible for sending truthful event content, although the current service does not authenticate them.

The prototype does not include multitenancy, identity integration, a regulator portal, regulator specific report formats, a durable signing identity, a key rotation design, external chain checkpoints, high availability, or operational monitoring.

The API and documentation also record known deviations from the original planning document, including response pagination shape, time range semantics, request correlation, error response shape, and missing integration tests. These deviations are documented rather than hidden.

## Production followup

Before production use, I would first add authentication, authorization, tenant isolation, and persistent key management. I would then add real PostgreSQL integration tests for concurrent writes and direct tampering. Scenario C would require an approved event contract, a complete inventory of account access paths, a reliability decision for audit outages, and audit logging for report access.

After those controls are in place, I would add monitoring for failed event capture, verification failures, storage growth, archive jobs, redaction failures, export activity, key health, and clock drift. A security review and compliance review would be required before making any external assurance claim.

## Engineer ownership

AI assistance was used for implementation support, test generation, review, debugging, and documentation. The accepted, modified, and rejected suggestions are recorded in AI_USAGE.md. The engineer remains responsible for reviewing the code, running the validation, understanding the design, and deciding whether the remaining risks are acceptable.
