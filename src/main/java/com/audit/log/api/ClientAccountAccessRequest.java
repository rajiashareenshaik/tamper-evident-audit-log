package com.audit.log.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scenario C contract for one attempt to read client account data.
 *
 * The fixed shape deliberately prevents callers from adding account content to the audit payload.
 * The actor is still caller supplied until producer authentication is implemented.
 */
public record ClientAccountAccessRequest(
        @NotBlank @Size(max = 200) String actorId,
        @NotBlank @Size(max = 200) String accountId,
        @NotNull Action action,
        @NotNull Outcome outcome,
        @NotBlank @Size(max = 100) String sourceApplication,
        @NotBlank @Size(max = 200) String requestId,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> dataCategories
) {
    public ClientAccountAccessRequest {
        dataCategories = dataCategories == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(dataCategories));
    }

    public enum Action {
        READ
    }

    public enum Outcome {
        ALLOWED,
        DENIED
    }
}
