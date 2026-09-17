package com.audit.log.integrity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
