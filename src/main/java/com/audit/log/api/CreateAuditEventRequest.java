package com.audit.log.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Inbound request to record a new audit event, as submitted by a caller.
 *
 * @param eventType what happened, e.g. {@code USER_LOGIN}, {@code RECORD_UPDATED}
 * @param actorId who or what caused the event
 * @param resourceType the type of resource affected
 * @param resourceId the specific resource affected
 * @param payload structured, event-specific detail
 */
public record CreateAuditEventRequest(

        @NotBlank
        String eventType,

        @NotBlank
        String actorId,

        @NotBlank
        String resourceType,

        @NotBlank
        String resourceId,

        @NotNull
        Map<String, Object> payload
) {
}
