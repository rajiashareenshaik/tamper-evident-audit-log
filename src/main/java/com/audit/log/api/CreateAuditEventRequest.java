package com.audit.log.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.List;

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
        Map<String, Object> payload,

        List<String> redactableFields
) {
    public CreateAuditEventRequest(String eventType, String actorId, String resourceType,
                                   String resourceId, Map<String, Object> payload) {
        this(eventType, actorId, resourceType, resourceId, payload, List.of());
    }

    public CreateAuditEventRequest {
        redactableFields = redactableFields == null ? List.of() : List.copyOf(redactableFields);
    }
}
