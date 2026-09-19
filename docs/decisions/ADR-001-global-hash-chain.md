# ADR-001: One global hash chain

## Status

Accepted.

## Context

Every audit event needs to be tamper-evident: if any stored row is altered or deleted outside the
application, that must be detectable. The standard mechanism is a hash chain, where each event's
`chainHash` is derived from its own content plus the previous event's `chainHash`
(`sha256(previousHash + contentHash)`), so altering any one event invalidates every hash after it.

The open design question was *scope*: one chain covering every event in the system, or many
chains (e.g., one per actor, one per resource, or a sharded set of chains).

## Decision

Use a single global chain. Every event, regardless of actor or resource, links to the same
sequence via one `audit_chain_head` row (`chain_id = 'GLOBAL'`). Writers acquire a row lock
(`SELECT ... FOR UPDATE`) on that single head row before computing an event's hashes, so
concurrent writers serialize instead of racing to extend the chain from a stale tip.

## Consequences

**Positive:**
- The verification story is as simple as it can be: read every row in `sequence_id` order,
  recompute hashes, compare to the stored chain head. One chain, one proof, no reconciliation
  across shards.
- A single global sequence gives a real, total order across the whole system — useful for
  full-chain audits and for reasoning about "what happened when" across different actors and
  resources.

**Negative / tradeoffs:**
- All writes serialize on one lock. Throughput is bounded by how fast one writer at a time can
  compute hashes and insert, not by how many partitions could otherwise run in parallel.
- Verifying a *filtered* subset (e.g., "just this actor's history," as bulk export does) is not
  self-contained: the previous/chain hash of a filtered record usually points to some other,
  unexported event. See [Scenario B: Bulk export](../scenario-b.md#bulk-export) for how the
  export bundle's chain anchor partially addresses this without a full redesign.

**Alternatives considered:**
- *Per-actor or per-resource chains*, each with its own head/lock: would allow write parallelism
  across different actors/resources, at the cost of losing a single total order and requiring a
  periodic checkpoint/Merkle-root step to still make a system-wide tamper-evidence claim. Rejected
  for this prototype as unnecessary complexity given the expected scale, but noted as the natural
  next step if write throughput ever becomes the bottleneck.
