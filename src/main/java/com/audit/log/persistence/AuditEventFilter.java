package com.audit.log.persistence;

import java.time.Instant;

/**
 * Optional filters and cursor pagination for {@link AuditEventRepository#findMatching}. A
 * {@code null} field means "no filter on this field".
 *
 * @param actorId restrict to events caused by this actor
 * @param resourceType restrict to events against this resource type
 * @param resourceId restrict to events against this specific resource
 * @param eventType restrict to events of this type
 * @param from restrict to events at or after this timestamp
 * @param to restrict to events at or before this timestamp
 * @param afterSequenceId cursor: only return events with a sequence_id greater than this
 * @param limit maximum number of events to return
 */
public record AuditEventFilter(
        String actorId,
        String resourceType,
        String resourceId,
        String eventType,
        Instant from,
        Instant to,
        Long afterSequenceId,
        int limit
) {
}
