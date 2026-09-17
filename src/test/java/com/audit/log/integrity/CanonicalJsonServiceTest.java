package com.audit.log.integrity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalJsonServiceTest {

    private final CanonicalJsonService service =
            new CanonicalJsonService(
                    JsonMapper.builder()
                            .findAndAddModules()
                            .build()
            );

    @Test
    void shouldProduceSameJsonRegardlessOfPayloadKeyOrder() {

        UUID eventId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        Instant timestamp =
                Instant.parse(
                        "2026-09-16T20:00:00Z"
                );

        Map<String, Object> firstPayload =
                new LinkedHashMap<>();

        firstPayload.put("accountId", "A100");
        firstPayload.put("action", "VIEW");

        Map<String, Object> secondPayload =
                new LinkedHashMap<>();

        secondPayload.put("action", "VIEW");
        secondPayload.put("accountId", "A100");

        CanonicalAuditContent first =
                new CanonicalAuditContent(
                        eventId,
                        "ACCOUNT_VIEWED",
                        "employee-123",
                        "ACCOUNT",
                        "A100",
                        firstPayload,
                        timestamp,
                        1
                );

        CanonicalAuditContent second =
                new CanonicalAuditContent(
                        eventId,
                        "ACCOUNT_VIEWED",
                        "employee-123",
                        "ACCOUNT",
                        "A100",
                        secondPayload,
                        timestamp,
                        1
                );

        assertEquals(
                service.canonicalize(first),
                service.canonicalize(second)
        );
    }
}
