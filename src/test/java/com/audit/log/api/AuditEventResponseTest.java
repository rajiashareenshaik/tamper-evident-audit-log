package com.audit.log.api;

import com.audit.log.domain.AuditEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("AuditEventResponse")
class AuditEventResponseTest {

    @Test
    @DisplayName("copies every field from the domain AuditEvent")
    void shouldCopyAllFieldsFromDomainEvent() {

        AuditEvent event = new AuditEvent(
                42L,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "USER_LOGIN",
                "user-1",
                "ACCOUNT",
                "A1",
                Map.of("k", "v"),
                Instant.parse("2026-09-17T10:15:30Z"),
                1,
                "content-hash",
                "previous-hash",
                "chain-hash"
        );

        AuditEventResponse response = AuditEventResponse.from(event);

        assertEquals(event.sequenceId(), response.sequenceId());
        assertEquals(event.eventId(), response.eventId());
        assertEquals(event.eventType(), response.eventType());
        assertEquals(event.actorId(), response.actorId());
        assertEquals(event.resourceType(), response.resourceType());
        assertEquals(event.resourceId(), response.resourceId());
        assertEquals(event.payload(), response.payload());
        assertEquals(event.eventTimestamp(), response.eventTimestamp());
        assertEquals(event.schemaVersion(), response.schemaVersion());
        assertEquals(event.contentHash(), response.contentHash());
        assertEquals(event.previousHash(), response.previousHash());
        assertEquals(event.chainHash(), response.chainHash());
    }
}
