package com.audit.log.persistence;

import com.audit.log.domain.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventRepository")
class AuditEventRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private JsonMapper jsonMapper;

    private AuditEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AuditEventRepository(jdbcTemplate, jsonMapper);
    }

    @Test
    @DisplayName("inserts the event and returns the Postgres-generated sequenceId")
    void shouldInsertAndReturnGeneratedSequenceId() {

        AuditEvent event = new AuditEvent(
                0L,
                UUID.randomUUID(),
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

        when(jsonMapper.writeValueAsString(event.payload())).thenReturn("{\"k\":\"v\"}");
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(42L);

        long sequenceId = repository.insert(event);

        assertEquals(42L, sequenceId);
    }

    @Test
    @DisplayName("fails fast with IllegalStateException and never touches the DB when the payload can't be serialized")
    void shouldFailFastWhenPayloadCannotBeSerialized() {

        AuditEvent event = new AuditEvent(
                0L,
                UUID.randomUUID(),
                "USER_LOGIN",
                "user-1",
                "ACCOUNT",
                "A1",
                Map.of("bad", new Object()),
                Instant.parse("2026-09-17T10:15:30Z"),
                1,
                "content-hash",
                "previous-hash",
                "chain-hash"
        );

        when(jsonMapper.writeValueAsString(event.payload()))
                .thenThrow(new RuntimeException("not serializable"));

        assertThrows(IllegalStateException.class, () -> repository.insert(event));

        verify(jdbcTemplate, never()).queryForObject(
                anyString(), eq(Long.class), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }
}
