# ADR-002: Server-assigned event timestamps

## Status

Accepted.

## Context

`CreateAuditEventRequest` lets a caller describe what happened, but not *when* the server should
record it as having happened. `eventTimestamp` is one of the fields that gets canonicalized and
hashed into `contentHash` (see `CanonicalAuditContent`), so whatever value is used becomes part of
the immutable, tamper-evident record. The question was whether that timestamp should be supplied
by the caller (client-asserted) or assigned by the server at the moment of insertion.

## Decision

The server assigns `eventTimestamp` from its own injected `Clock` (`AuditCommandService.create`
calls `clock.instant()`), immediately before hashing. Callers cannot supply or influence this
value.

## Consequences

**Positive:**
- A caller cannot backdate or reorder events relative to the chain they're being inserted into.
  Since the chain's order is the insertion order, and the timestamp is bound to that same instant,
  the two can never be made to disagree.
- Retention (`RetentionService.archiveExpired`) can trust `event_timestamp` as an honest "when did
  this happen" signal for cutoff calculations, without needing to separately validate a
  caller-supplied value.
- Using one injected `Clock` bean (rather than `Instant.now()` scattered through the code) keeps
  time-dependent logic testable with `Clock.fixed(...)`, which every test in this codebase relies
  on.

**Negative / tradeoffs:**
- The service cannot represent "this event actually happened at time X, but we only learned about
  it now" (a common need for replayed/backfilled/late-arriving events in some audit systems). If
  that becomes a requirement, it would need a second, explicitly-named field (e.g.
  `occurredAt`) that is *not* treated as authoritative for chain ordering, kept separate from the
  server-assigned `eventTimestamp` that anchors the hash chain.
- Clock skew or drift on the server host directly affects recorded timestamps. This is an accepted
  risk for the prototype; a production deployment would want NTP-disciplined hosts and probably a
  monotonic sanity check (reject if `clock.instant()` moves backwards relative to the last
  recorded event).

**Alternatives considered:**
- *Caller-supplied timestamp, validated against a tolerance window*: rejected because it
  reintroduces exactly the tamper vector this design exists to close — a caller (or a compromised
  upstream system) could still assert a timestamp anywhere inside the tolerance window, weakening
  the "when" guarantee for a marginal usability gain.
