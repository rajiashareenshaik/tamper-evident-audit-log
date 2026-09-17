package com.audit.log.api;

import com.audit.log.domain.AuditEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound representation of a persisted audit event, as returned to callers.
 *
 * @param sequenceId the DB-assigned, strictly increasing position of this event in the chain
 * @param eventId caller-visible unique identifier for this event
 * @param eventType what happened, e.g. {@code USER_LOGIN}, {@code RECORD_UPDATED}
 * @param actorId who or what caused the event
 * @param resourceType the type of resource affected
 * @param resourceId the specific resource affected
 * @param payload structured, event-specific detail
 * @param eventTimestamp server-assigned time the event was recorded
 * @param schemaVersion version of this record's shape
 * @param contentHash SHA-256 of this event's own canonicalized content
 * @param previousHash chainHash of the event immediately before this one in the global chain
 * @param chainHash SHA-256 of {@code previousHash + contentHash}, linking this event to the chain
 */
public record AuditEventResponse(
        long sequenceId,
        UUID eventId,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        Map<String, Object> payload,
        Instant eventTimestamp,
        int schemaVersion,
        String contentHash,
        String previousHash,
        String chainHash
) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.sequenceId(),
                event.eventId(),
                event.eventType(),
                event.actorId(),
                event.resourceType(),
                event.resourceId(),
                event.payload(),
                event.eventTimestamp(),
                event.schemaVersion(),
                event.contentHash(),
                event.previousHash(),
                event.chainHash()
        );
    }
}
