package com.audit.log.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
