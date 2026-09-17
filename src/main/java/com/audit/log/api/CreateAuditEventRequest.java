package com.audit.log.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

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
