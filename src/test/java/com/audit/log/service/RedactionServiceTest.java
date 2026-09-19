package com.audit.log.service;

import com.audit.log.domain.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedactionServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RedactionService service = new RedactionService(jdbc, JsonMapper.builder().build(),
            Clock.systemUTC(), "test-only-key");

    @Test void replacesNestedSensitiveDataWithACommitmentAndEncryptsTheOriginal() {
        var prepared = service.prepare(UUID.randomUUID(),
                Map.of("profile", Map.of("ssn", "123-45-6789", "name", "Ada")), List.of("profile.ssn"));
        String stored = prepared.payload().toString();
        assertFalse(stored.contains("123-45-6789"));
        assertTrue(stored.contains("commitment"));
        assertFalse(prepared.secrets().get(0).ciphertext().contains("123-45-6789"));
        assertEquals("profile.ssn", prepared.secrets().get(0).path());
    }

    @Test void rejectsUnknownAndDuplicatePathsCleanly() {
        assertThrows(IllegalArgumentException.class, () ->
                service.prepare(UUID.randomUUID(), Map.of("name", "Ada"), List.of("ssn")));
        var prepared = service.prepare(UUID.randomUUID(), Map.of("ssn", "x"), List.of("ssn", "ssn"));
        assertEquals(1, prepared.secrets().size());
    }

    @Test void identicalValuesProduceDifferentCommitmentsAcrossFieldsAndEvents() {
        var first = service.prepare(UUID.randomUUID(), Map.of("ssn", "123-45-6789"), List.of("ssn"));
        var second = service.prepare(UUID.randomUUID(), Map.of("ssn", "123-45-6789"), List.of("ssn"));

        String firstCommitment = commitmentOf(first.payload().get("ssn"));
        String secondCommitment = commitmentOf(second.payload().get("ssn"));

        assertNotEquals(firstCommitment, secondCommitment);
    }

    @SuppressWarnings("unchecked")
    private String commitmentOf(Object placeholder) {
        return (String) ((Map<String, Object>) placeholder).get("commitment");
    }

    @Test void hydratingAForgedMarkerReturnsItUnchangedInsteadOfThrowing() {
        Map<String, Object> forged = Map.of(
                "_redactionId", "not-a-real-uuid",
                "commitment", "whatever"
        );

        AuditEvent event = new AuditEvent(1L, UUID.randomUUID(), "TYPE", "actor", "RESOURCE", "id",
                Map.of("field", forged), Instant.parse("2026-09-17T00:00:00Z"), 1,
                "content-hash", "previous-hash", "chain-hash");

        AuditEvent hydrated = service.hydrate(event);

        assertEquals(forged, hydrated.payload().get("field"));
        verifyNoInteractions(jdbc);
    }
}
