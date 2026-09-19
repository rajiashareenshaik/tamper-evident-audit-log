# Scenario C Compliance Reporting

## Original requirement

Product said:

Regulators need to be able to audit access to client account data.

I did not treat that sentence as an implementation specification. It explains the outcome Product wants, but it does not define what counts as access, who produces the evidence, who may retrieve it, or what a regulator expects to receive.

## Clarification before writing code

I would review the following questions with Product, Compliance, Security, and the teams that own client account data.

1. What counts as access? Does it include viewing an account, searching for an account, downloading a statement, changing data, batch processing, support impersonation, and direct database access?
2. Should denied and failed attempts be recorded, or only successful access?
3. Which people and systems are in scope? This may include employees, administrators, service accounts, vendors, and automated jobs.
4. Is the actor identity supplied by the source application, or must the audit service verify it?
5. What account identifier can be stored without adding unnecessary client information to the log?
6. Do we need the reason for access, the application used, the request identifier, and the categories of data viewed?
7. Does a regulator use the service directly, or does an internal compliance team prepare and approve an export?
8. Which jurisdiction and policy define retention, legal hold, residency, and deletion requirements?
9. What report format, delivery process, and response time are required?
10. What level of evidence is expected? A hash chain detects some changes, but it does not prove that every source application reported every access.

These questions affect the event contract, trust model, access controls, storage policy, and integration design. I would not infer legal obligations or a retention period from the original sentence.

## Working assumptions

No stakeholder answers were available for this prototype, so I used the following assumptions to create a reviewable design.

Access means an application level attempt to read client account data. Successful and denied attempts are both in scope. Account changes and bulk downloads should use separate event types because they have different meaning and risk.

Employees and service accounts are in scope. The source application has already authenticated the actor and sends a stable actor identifier. This is only an assumption for the design. The current service does not verify that claim.

An internal compliance team retrieves and approves reports for regulators. Direct regulator accounts and a regulator portal are out of scope for this prototype.

The audit event contains a reference to the account, not the account balance, full account record, credentials, or other client content. The event can include data categories that were viewed, but should not copy those values into the audit log.

The server timestamp means the time the audit service accepted the event. It is not proof of the exact time when access occurred in another system. If that distinction matters, a separate source occurrence time must be added and clearly labeled as caller supplied.

## Clarified requirement

The system must accept audit events from approved source applications for successful and denied attempts to read client account data.

Each access event must identify the actor, the client account reference, the action, the outcome, the source application, and a request identifier. It may include the categories of data viewed when the source application can provide them. The audit service must assign the recording time. The event must not contain the underlying account data.

Authorized compliance staff must be able to search access events by actor, account, event type, and recording period. They must be able to export relevant records for review. The stored history must provide evidence that changes to recorded events can be detected within the documented threat model.

The applications that must produce these events, the authorization model, tenant isolation rules, retention policy, and regulator delivery format must be approved before production use.

## Proposed event contract

I would use the existing event API with a defined convention for client account access.

```json
{
  "eventType": "CLIENT_ACCOUNT_ACCESS",
  "actorId": "employee-123",
  "resourceType": "CLIENT_ACCOUNT",
  "resourceId": "account-456",
  "payload": {
    "action": "READ",
    "outcome": "ALLOWED",
    "sourceApplication": "CSR_PORTAL",
    "requestId": "request-789",
    "dataCategories": ["CONTACT_DETAILS"]
  }
}
```

For this event type, action, outcome, sourceApplication, and requestId should be required. Outcome should accept only agreed values such as ALLOWED and DENIED. Data categories should come from a controlled list. Extra payload fields should be rejected or filtered so a caller cannot accidentally place account data in the event.

This contract is implemented by the dedicated Scenario C endpoint. Its typed request accepts only these fields, requires the identity, account, action, outcome, source, and request values, and limits list and string sizes. Action currently accepts only READ. Outcome accepts ALLOWED or DENIED. The general purpose event endpoint remains available for other event types and is not a substitute for this contract.

## Technical design

The design builds on the existing Spring Boot and PostgreSQL audit service.

The source account application makes its authorization decision and submits the access event to POST /api/v1/audit/client-account-access. ClientAccountAccessService maps the typed request to fixed CLIENT_ACCOUNT_ACCESS and CLIENT_ACCOUNT values. The existing audit write path assigns an event identifier and server timestamp, converts the event to canonical JSON, hashes the content, links it to the previous event, and stores the event and new chain head in one transaction.

GET /api/v1/audit/client-account-access provides the Scenario C query. It fixes the resource and event types so results contain only client account access records. It accepts optional actor, account, recording time, sequence cursor, and page size filters. It uses the existing sequence based pagination and returns records in chain order. The current time range is inclusive at both ends.

The verification endpoint recomputes the complete hash chain and reports the first inconsistency. This supports an integrity check before preparing a report.

The export endpoint can export all records for one actor or one resource. It includes archived records, record hashes, a chain anchor, a digest, and a signature. A filtered export is not a full proof that no matching records were omitted. The public key is also carried inside the bundle and the signing key changes when the application restarts, so the current export is a prototype rather than durable regulator evidence.

The normal query API hides soft archived events, while export and full chain verification include them. A compliance workflow must use the correct path and state whether archived records are included.

## Task decomposition

I separated Scenario C into work that can be completed with the current decisions and work that depends on stakeholder answers.

1. Clarify the access boundary, actors, report users, event fields, retention policy, and evidence expectations.
2. Define and approve the CLIENT_ACCOUNT_ACCESS event contract.
3. Add validation for required payload fields, allowed action and outcome values, bounded data categories, and prohibited extra fields. This is implemented by the typed request.
4. Add trusted producer authentication and derive actor identity from verified credentials where possible.
5. Integrate access event creation into every approved account data path.
6. Decide whether an account read must fail when the audit service is unavailable, or whether a durable local outbox may record it for later delivery.
7. Add compliance authorization, tenant isolation, and audit logging for report and export access.
8. Extend export filters if reviewers need account, actor, outcome, and time range in one report.
9. Add integration tests for capture failures, retries, duplicate delivery, tenant isolation, archived events, and report authorization.
10. Run chain verification before export and document the review and approval procedure.

The first two tasks come before implementation because they define what the code must guarantee. Reliable capture and authorization come before calling the feature production ready.

## What is implemented

The repository contains a partial Scenario C implementation that can accept and query a fixed client account access event contract, then use the existing audit platform to verify, retain, redact, and export submitted events.

The implemented foundation includes the following behavior.

1. A write API for general audit events.
2. Server assigned event identifiers and timestamps.
3. Canonical content hashing and a global hash chain.
4. Transactional event and chain head updates.
5. Query filters for actor, resource, event type, and time range.
6. Cursor based pagination.
7. Full chain verification with a first failure reason.
8. Soft archival that keeps physical events available for verification.
9. Structured redaction using a stable commitment and separately stored encrypted value.
10. Signed export for one actor or one resource, including archived records and chain metadata.
11. A dedicated client account access write endpoint with a fixed payload shape.
12. A dedicated client account access query with actor, account, time, cursor, and limit filters.
13. Validation for required access fields, READ actions, ALLOWED or DENIED outcomes, field lengths, and data category entries.

These features demonstrate the storage and integrity foundation for a compliance report. They do not complete end to end access auditing.

## What is not implemented

The service does not automatically observe client account access. Another application must call the write API, and there is no transaction connecting an account read to the audit write. The service therefore cannot prove that every access was recorded. It also cannot prevent an account read when audit recording fails.

The service has no authentication, producer identity verification, compliance role, tenant boundary, or report approval workflow. The caller supplies actorId. Every endpoint is reachable by any caller who can reach the service. This is the highest priority production gap.

The dedicated Scenario C endpoint enforces its payload contract. The generic audit endpoint can still accept a caller constructed CLIENT_ACCOUNT_ACCESS event because the system has no event type registry that reserves that name. Production hardening should reserve managed event types or restrict the generic endpoint to trusted producers.

Reading or exporting audit records is not itself recorded as an audit event. There is no direct regulator interface or regulator specific report template.

The hash chain makes inconsistent edits detectable during verification, but a privileged database administrator can rewrite the events and rebuild the chain head. Independent periodic checkpoints would reduce this risk.

The export signature uses a key generated at application startup. Production use needs a persistent key protected by a key management service and a trusted way to distribute the public key.

The retention window is a configurable engineering default. It has not been approved as a legal or compliance policy. Soft archival also keeps the original row, so it does not reclaim storage or satisfy a requirement for physical deletion.

## Validation plan

The existing tests cover event creation, hashing, query construction, chain verification, retention, redaction, and export behavior. Scenario C unit tests cover request validation, fixed event mapping, fixed report filters, time range validation, page limits, and controller responses. They do not validate Scenario C as an end to end feature connected to an account application.

The remaining end to end validation should cover the following cases after a source account application and authorization model are selected.

1. A successful account read creates one ALLOWED event with all required fields.
2. A denied read creates one DENIED event and returns no account data.
3. The HTTP layer rejects missing or invalid compliance fields.
4. The fixed request shape cannot accept sensitive account values.
5. A compliance user can retrieve only the permitted tenant and time range.
6. An unauthorized user cannot query or export access events.
7. A report access or export creates its own audit event.
8. Archived events appear in the approved compliance export.
9. Failed delivery, retry, and duplicate requests follow the selected reliability policy.
10. Direct database modification causes chain verification to report the first inconsistency.

I would also add a PostgreSQL integration test for concurrent writes and a real database tamper test. The current mocked tests validate logic, but they do not prove row locking and out of band tamper detection in a running database.

## Scope decision

I chose to implement the event contract and focused query while still labeling Scenario C as partial. The repository now provides Scenario C specific code in addition to storage, integrity, query, retention, redaction, and export capabilities. The missing capture and authorization boundaries are central to the requirement, not optional polish.

I scoped out direct regulator access, regulator specific templates, legal retention interpretation, external checkpoint publication, and production key management because those require policy, security, and operational decisions that were not provided. I also scoped out automatic integration with account systems because no source application or access paths were supplied in the assignment.

The next review point is approval of the event contract, access inventory, reliability policy, authorization model, and retention rules. Code written before those decisions would create an appearance of compliance without a defensible guarantee.
