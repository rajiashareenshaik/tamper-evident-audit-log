package com.audit.log.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
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
}
