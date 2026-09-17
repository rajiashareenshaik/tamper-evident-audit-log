package com.audit.log.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A single, hash-chained entry in the global audit log, as persisted in {@code audit_event}.
 *
 * @param sequenceId the DB-assigned, strictly increasing position of this event in the chain
 * @param eventId caller-visible unique identifier for this event
 * @param eventType what happened, e.g. {@code USER_LOGIN}, {@code RECORD_UPDATED}
 * @param actorId who or what caused the event
 * @param resourceType the type of resource affected
 * @param resourceId the specific resource affected
 * @param payload structured, event-specific detail
 * @param eventTimestamp server-assigned time the event was recorded; part of the hashed content
 * @param schemaVersion version of this record's shape, for forward-compatible evolution
 * @param contentHash SHA-256 of this event's own canonicalized content
 * @param previousHash chainHash of the event immediately before this one in the global chain
 * @param chainHash SHA-256 of {@code previousHash + contentHash}, linking this event to the chain
 */
public record AuditEvent(
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
}
