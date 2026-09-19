# Prompt Log

Chronological summary of prompts and key decisions from this session, working on the
tamper-evident audit log service's write/read API, persistence layer, and tests.

## Write path: POST /api/v1/audit/events

- **Prompt:** Define the `POST /api/v1/audit/events` API call.
- **Decision:** Added `AuditWriteController` (later renamed `AuditController`, see below) and
  `AuditEventResponse` (outbound DTO, decoupled from the domain `AuditEvent`). Wired to the
  existing `AuditCommandService`. Flagged that `idempotency_key` had no unique constraint in the
  schema, so the `Idempotency-Key` header wasn't actually enforced as a dedup key.

## AuditCommandService rebuild

- **Prompt:** Add a `create` method to `AuditCommandService` (file was mid-edit and briefly in a
  broken/incomplete state — fields and constructor missing, method body cut off).
- **Decision:** Restored the class incrementally as the user iterated: constructor, then a
  `create(CreateAuditEventRequest)` method. Flagged each time that the class temporarily couldn't
  persist anything (no repository wiring, hardcoded genesis hash) until fully reconnected later.
- **Prompt:** Review `ChainHeadRepository.lockOrCreate`.
- **Decision:** Confirmed the upsert-then-`SELECT ... FOR UPDATE` pattern is correct for
  Postgres, but flagged that the row lock is only meaningful inside a transaction the caller
  owns — nothing enforces that, so a missing `@Transactional` would silently disable the
  concurrency guarantee.
- **Prompt:** Build failed — controller called `record(request, idempotencyKey)`, which no longer
  existed on `AuditCommandService`; `AuditEventRepository` was reported missing.
- **Decision:** Fixed the controller to call `create(request)` instead, and removed the now-dead
  `Idempotency-Key` header param. The `AuditEventRepository` error was stale (already resolved by
  the time it was reported).
- **Prompt:** Recreate `AuditEventRepository` with only an `insert` method (JSONB payload,
  Postgres-generated `sequence_id` via `RETURNING`), no update/delete.
- **Decision:** Rebuilt `AuditEventRepository.insert`, deliberately omitting `idempotency_key`
  since `AuditCommandService.create` didn't take one at that point.
- **Prompt:** Add `ChainHeadRepository.update` (sets `last_sequence_id`, `last_event_id`,
  `last_chain_hash`, `event_count`; throws if not exactly one row updated).
- **Decision:** Implemented as specified.
- **Prompt:** Wire `AuditCommandService` to actually lock the chain head, chain off the current
  tip's `lastChainHash` (not genesis), insert, and advance the head — all in one transaction.
- **Decision:** Restored `ChainHeadRepository.lockOrCreate`'s body (it had been emptied out along
  the way) and rebuilt `AuditCommandService.create` to: lock/read the head, compute
  `contentHash`/`chainHash` off the real tip, insert via `AuditEventRepository`, advance the head,
  return the persisted `AuditEvent` — under `@Transactional`.

## Test suite

- **Prompt:** Generate unit tests for all classes.
- **Decision:** Added tests for every class with actual behavior — `AuditCommandService`,
  `AuditWriteController`, `AuditEventResponse`, `CreateAuditEventRequest` (validation),
  `ClockConfig`, `ChainHeadRepository`, `AuditEventRepository` — 27 tests total by session end.
  Deliberately skipped plain data records (`AuditEvent`, `ChainHead`, `CanonicalAuditContent`)
  since they have no logic to verify.
- **Prompt:** A generated test failed — `AuditEventRepositoryTest.shouldFailFastWhenPayloadCannotBeSerialized`
  threw `NullPointerException` instead of the expected `IllegalStateException`.
- **Decision:** Root-caused to a wrong assumption: Jackson 3's `JsonMapper` doesn't actually fail
  serializing an empty `Object`, so the real bug was an unstubbed mock `queryForObject` returning
  `null`, unboxed into a primitive `long`. Fixed by mocking `JsonMapper` directly (it's a
  non-final class) and stubbing a deterministic failure, plus asserting the DB is never touched
  on that path.
- **Prompt:** For each scenario covered in the unit tests, check whether the primary use cases are
  covered.
- **Decision:** Reviewed all 7 test files scenario-by-scenario. Confirmed the core append flow is
  covered end-to-end (controller → service → both repositories, happy path + failure path).
  Flagged real gaps: no `@WebMvcTest`/`MockMvc` coverage of HTTP-level validation (400 responses),
  and chain-head row-locking/concurrency can only be verified with a real-Postgres integration
  test, not mocks.
- **Prompt:** Add test descriptions.
- **Decision:** Added `@DisplayName` to every test class and method across all test files,
  including the two pre-existing ones (`HashServiceTest`, `CanonicalJsonServiceTest`), for
  consistency.

## Locking design discussion (no code changed)

- **Prompt:** Propose a different locking mechanism for maintaining the tamper-evident hash
  chain.
- **Decision (discussion only):** Presented optimistic compare-and-swap on the chain head vs. a
  Postgres advisory lock as alternatives to `SELECT ... FOR UPDATE`; recommended the optimistic
  CAS approach.
- **Prompt:** What about options other than a global lock?
- **Decision (discussion only):** Presented partitioned chains with periodic Merkle-root
  checkpointing vs. decoupling insert from hashing ("sequence-then-seal" via an async sealer).
  Recommended sequence-then-seal as the smaller change since it preserves strict global ordering.

## Running the app locally

- **Prompt:** Add a "local run commands" section to the README.
- **Decision:** Added a "Running Locally" section (Docker Compose for Postgres, `./mvnw
  spring-boot:run`, `./mvnw test`, `./mvnw clean package`) based on the actual
  `docker-compose.yml`/`application.yml` values already in the repo.
- **Prompt:** Run `./mvnw spring-boot:run`.
- **Decision:** Found a Homebrew-installed JDK 21 (`/opt/homebrew/opt/openjdk@21`) since the
  shell's default `java` was JDK 8, which can't build this Java-21 project. Started Postgres via
  `docker compose` (already healthy). First `spring-boot:run` attempt failed on "port 8080 already
  in use" — an earlier instance was already running and healthy; verified it via
  `/actuator/health` and an end-to-end `POST` instead of starting a duplicate.
- **Prompt (mid-turn):** Check Postgres via `docker exec -it audit-postgres psql -U audit -d
  auditdb`.
- **Decision:** Ran a non-interactive equivalent (`-it` doesn't work without a TTY) and confirmed
  `audit_event`, `audit_chain_head`, and `flyway_schema_history` all exist.

## Read path: filters, cursor pagination, and where it lives

- **Prompt:** Add `AuditQueryRepository` using `JdbcTemplate`, with filters for `actorId`,
  `resourceType`, `resourceId`, `eventType`.
- **Decision:** Created `AuditEventFilter` (record) and `AuditQueryRepository.findMatching`
  (dynamic `AND`-chained SQL, ordered by `sequence_id`), reusing the existing `AuditEvent` domain
  record as the read model instead of inventing a new type.
- **Prompt:** Put the code in `AuditEventRepository` instead.
- **Decision:** Merged `findMatching`, its `RowMapper`, and payload deserialization into
  `AuditEventRepository`; deleted `AuditQueryRepository`.
- **Prompt:** Also add `from`, `to`, and `sequence_id` cursor pagination; keep the SQL simple,
  `AND` filters, ordered by `sequence_id` ascending.
- **Decision:** Extended `AuditEventFilter` with `from`/`to` (`Instant`) and
  `afterSequenceId`/`limit`; `findMatching` appends them as more flat `AND` conditions plus
  `ORDER BY sequence_id ASC LIMIT ?`. This exact request was repeated twice more in later turns;
  both times the implementation already matched, so nothing changed — the second repeat led to
  asking the user what was actually needed, which surfaced the next step (wire it to an API).
- **Prompt:** Wire the repository to an API and add tests.
- **Decision:** Added `AuditReadController` (`GET /api/v1/audit/events`, limit default 50 / cap
  500) and its unit tests, plus `findMatching` test coverage in `AuditEventRepositoryTest`
  (AND-condition ordering, null-field omission, row-to-`AuditEvent` mapping).
- **Prompt:** Put the read code in the same controller and same service class as the write path.
- **Decision:** Merged the `GET` endpoint into the write controller and added a thin
  `findMatching` passthrough to `AuditCommandService`; deleted the separate read controller.
  Mid-merge, a conflicting prompt arrived asking for a *separate* `AuditQueryService` with its own
  validation and a different default limit (100 vs. 50) — flagged the direct contradiction and
  asked which direction to take; the question was declined without a pick, so the consolidated
  version (one controller, one service) was left in place and the separate-service request was
  **not** implemented.
- **Prompt:** Fix a test-compile failure (stale `AuditReadControllerTest` referencing a deleted
  class) — the user had, in parallel, manually renamed `AuditWriteController`/
  `AuditWriteControllerTest` to `AuditController`/`AuditControllerTest`.
- **Decision:** Deleted the stale test file, fixed a leftover `@DisplayName` from the rename, and
  ran a clean rebuild (stale `target/` classes from the renames had been masking the real error).
  Verified with `JAVA_HOME` pointed at the Homebrew JDK 21: `BUILD SUCCESS`, 27/27 tests passing.

## Open items at end of session

- Whether to split query logic into a dedicated `AuditQueryService` (with `from`/`to` validation
  and a limit default of 100) is still undecided — currently consolidated into
  `AuditCommandService` instead.
- No `@WebMvcTest`/`MockMvc` test exists for HTTP-level request validation (400 responses).
- `idempotency_key` has no unique constraint and isn't enforced as a dedup key.
- Chain-head row-locking/concurrency behavior is only reviewed by inspection and mocked unit
  tests, not verified against real Postgres under concurrent load.

## Scenario B: risk review before implementation

- **Prompt:** Design Scenario B around three features — soft archival for retention, structured
  redaction without changing existing hashes, and verifiable bulk export. Identify implementation
  risks and wait for approval before writing code.
- **Decision (analysis only):** Found that Scenario B was already substantially implemented
  (`RetentionService`, `RedactionService`, `ExportService` and their controllers, plus
  `docs/scenario-b.md`) from work outside this conversation. Reviewed the actual code and flagged
  concrete risks per feature: unbounded first-run archival with no reversal path; a redaction
  commitment with no domain separation (identical values across fields/events produce identical
  commitments); one key reused for both HMAC and AES-GCM; a crafted payload that could crash
  hydration on every future read of a record; an export signing key that's regenerated on every
  restart (no durable identity); export bypassing redaction hydration; and no authentication on
  any Scenario B endpoint. No code changed in this step.

## Retention review and fixes

- **Prompt:** Review the retention implementation against Scenario B's requirements (archive
  eligibility, exclusion from normal queries, inclusion in verification, no mutation of
  `audit_event`, idempotency, cutoff correctness, test coverage).
- **Decision:** Confirmed all behavioral requirements were already met by inspection. Found and
  documented test-coverage gaps only (no defects): missing negative-window test, no test asserting
  the exact cutoff comparison operator/bind order, no test proving a second archival run reports
  zero, and zero test coverage of the archive-exclusion guarantee at the repository level.

## Structured redaction: fixes and review

- **Prompt:** Implement the structured-redaction design (separate encrypted-value store, stable
  placeholder/commitment, hydrate-until-redacted, destroy-on-redact, chain-verification
  unaffected, idempotent redaction, secrets from config).
- **Decision:** Found this was already implemented matching the spec; asked whether to fix the
  specific risks already identified or rebuild from scratch. Chose to fix in place. Applied three
  changes to `RedactionService`: (1) bound the HMAC commitment to `redactionId + eventId + path +
  value` instead of just the value, so identical secrets no longer produce identical commitments;
  (2) derived two independent subkeys (domain-separated SHA-256) for encryption vs. commitment
  instead of reusing one key for both; (3) made `hydrateValue` catch a non-UUID `_redactionId` and
  return the data unchanged instead of throwing, closing a crash-on-read path a caller could
  trigger by injecting a marker-shaped payload directly. Added three tests proving each fix.
- **Prompt:** Review the structured-redaction implementation for correctness and security against
  a nine-point checklist (never rewrites `audit_event`, stable hash input, no plaintext stored,
  authenticated encryption with a fresh nonce, no committed secrets, safe path rejection,
  idempotent redaction, values unrecoverable via any API, verification tested before/after
  redaction). Explain weaknesses before changing code.
- **Decision (analysis only):** Confirmed seven of nine items were already correct by inspection
  and grep (including that no real secret is committed to Git). Found two remaining weaknesses:
  `redact()` silently no-ops on an invalid/nonexistent field path instead of rejecting it
  distinctly, and the AES-GCM ciphertext has no associated data binding it to its
  `redactionId`/`eventId`/`path` row (a ciphertext copied between rows would still decrypt). Also
  flagged that the "verify before and after redaction" claim had no test yet. No code changed in
  this step, per the explicit instruction to explain first.

## Verifiable bulk export: closing the boundary-metadata gap

- **Prompt:** Implement the verifiable bulk-export design (single-filter export, active +
  archived records, self-contained signed bundle with per-event hashes, boundary/chain metadata
  for independent verification, reject both/neither filters, ascending order, controller/DTOs/
  service/repository/tests). Don't change retention or redaction unless required; explain any
  cross-feature change first.
- **Decision:** Found six of eight requirements already implemented. The one real gap: exported
  bundles had no anchor back to the real chain, so a recipient couldn't tell whether the first
  exported record's `previousHash` was genuine or how many events existed at export time.
  Explained the cross-feature touch needed (read-only access to `ChainHeadRepository`/
  `HashService`, both pre-existing Scenario A components — no changes to retention or redaction)
  and implemented it: added `ExportBundle.ChainAnchor` (genesis hash, chain head hash, event
  count, last sequence id), included it in the signed digest alongside the records, and rewrote
  the export section of `docs/scenario-b.md` to describe honestly what the anchor does and does
  not prove (a checkpoint, not a full inclusion proof). Added four tests for the new behavior.

## Full Scenario B test suite review

- **Prompt:** Run and review the complete Scenario B test suite against a 17-point scenario
  checklist spanning retention, redaction, and export. Fix only confirmed defects; summarize
  every change.
- **Decision:** Ran the existing suite as a baseline (39 passing), then mapped every checklist
  item to existing coverage. No behavioral defects were found anywhere — every gap was a missing
  test, not a bug. Added 14 new tests across `RetentionServiceTest`, `AuditEventRepositoryTest`
  (which had zero coverage of `findAllByActorOrResource`/`findAllForVerification` before this),
  and `ExportServiceTest`, plus one new file, `RedactionVerificationEndToEndTest`, which wires
  real `RedactionService` + `ChainVerificationService` instances together (with a small in-memory
  fake for the `audit_redaction_value` table) to prove verification is intact both before and
  after redaction against the same stored row — the single most important gap in the checklist.
  Finished at 53/53 tests passing, zero production code changes.

## Architecture diagram and design-decision docs

- **Prompt:** Generate a Scenario A + B architecture diagram with design decisions and tradeoffs,
  and update it in the appropriate engineering doc.
- **Decision:** Rewrote `docs/architecture.md` (previously stale, pre-implementation "will be"
  prose) with a current-state overview, a Mermaid component diagram covering the full Scenario
  A + B wiring, and a design-decisions/tradeoffs table linking to full detail rather than
  duplicating it. Found `docs/decisions/ADR-001-global-hash-chain.md` and
  `ADR-002-server-timestamps.md` existed as empty placeholder files and filled them in with real
  Status/Context/Decision/Consequences/Alternatives content, since they were the evident intended
  home for that material. Left `docs/scenario-b.md`'s existing tradeoff writeups untouched and
  linked to them instead of repeating them.

## Open items after Scenario B work

- No authentication/authorization exists on any endpoint, including irreversible redaction and
  bulk export — flagged repeatedly as the highest-severity open gap, not yet addressed.
- `redact()` accepts an invalid/nonexistent field path silently (returns 0 changed) rather than
  rejecting it distinctly from "already redacted."
- AES-GCM ciphertext in `audit_redaction_value` has no associated-data binding to its row context.
- The export signing key is regenerated on every app restart/replica, so bundles can't be
  cross-verified across instances or restarts; no durable signing identity exists yet.
- Retention's cutoff-boundary and repeated-archival tests are mock-based (consistent with the
  rest of the suite) and don't independently verify behavior against a live Postgres instance.

## Scenario C requirement normalization and final review

- **Prompt:** Review the full assignment and the existing repository, then produce a natural, plain language
  Scenario C submission that does not overstate the implementation.
- **Decision:** Expanded `docs/scenario-c.md` from a short assumptions note into a clarified requirement,
  proposed event contract, technical design, task decomposition, implemented scope, missing controls,
  validation plan, and scope decision. The documentation explicitly treats Scenario C as a partial
  implementation because the service does not capture account access automatically and has no
  authentication or report authorization.
- **Decision:** Replaced the placeholder `FINAL_ENGINEERING_SUMMARY.md` with a repository-wide summary covering
  rationale, artifacts, design decisions, validation, risks, assumptions, limitations, and production
  followup. Updated the README and requirements status so they do not claim that Scenario C is complete.
- **Engineer review required:** Confirm that the proposed `CLIENT_ACCOUNT_ACCESS` event fields match the intended
  product policy before implementing validation or source application integration. Confirm all stated test
  results on the submission machine and complete `ATTESTATION.md` personally before submission.
- **Prompt:** The Scenario C documentation was visible, but no Scenario C implementation files were present.
- **Decision:** Added a dedicated typed request, service, and controller for recording and querying client
  account access events. The typed request prevents arbitrary account content from being attached through
  this endpoint and restricts actions and outcomes to the working assumptions. Added nine focused tests for
  validation, mapping, query boundaries, and controller responses. The full suite passes with 62 tests.
- **Limitation retained:** The implementation records events submitted by another application. It does not
  authenticate producers, observe account reads directly, enforce tenant boundaries, or authorize reviewers.
