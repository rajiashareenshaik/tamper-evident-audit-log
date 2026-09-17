package com.audit.log.integrity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The fields of an audit event that get canonicalized and hashed to produce its
 * {@code contentHash}. Deliberately excludes {@code previousHash}/{@code chainHash}, which are
 * derived from this content rather than part of it.
 *
 * @param eventId caller-visible unique identifier for this event
 * @param eventType what happened, e.g. {@code USER_LOGIN}, {@code RECORD_UPDATED}
 * @param actorId who or what caused the event
 * @param resourceType the type of resource affected
 * @param resourceId the specific resource affected
 * @param payload structured, event-specific detail
 * @param eventTimestamp server-assigned time the event was recorded
 * @param schemaVersion version of this record's shape, for forward-compatible evolution
 */
public record CanonicalAuditContent(
        UUID eventId,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        Map<String, Object> payload,
        Instant eventTimestamp,
        int schemaVersion
) {
}
