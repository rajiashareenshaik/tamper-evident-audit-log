package com.audit.log.persistence;

import com.audit.log.domain.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("appends an AND condition and bind arg for every non-null filter field, in order")
    void shouldApplyAllFiltersAsAndConditionsInOrder() {

        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-30T00:00:00Z");

        AuditEventFilter filter = new AuditEventFilter(
                "user-1",
                "ACCOUNT",
                "A1",
                "USER_LOGIN",
                from,
                to,
                5L,
                10
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        repository.findMatching(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("audit_event_archive"), "findMatching must exclude archived events");
        assertTrue(sql.contains("AND actor_id = ?"));
        assertTrue(sql.contains("AND resource_type = ?"));
        assertTrue(sql.contains("AND resource_id = ?"));
        assertTrue(sql.contains("AND event_type = ?"));
        assertTrue(sql.contains("AND event_timestamp >= ?"));
        assertTrue(sql.contains("AND event_timestamp <= ?"));
        assertTrue(sql.contains("AND sequence_id > ?"));
        assertTrue(sql.contains("ORDER BY sequence_id ASC LIMIT ?"));

        assertArrayEquals(
                new Object[] {
                        "user-1", "ACCOUNT", "A1", "USER_LOGIN",
                        Timestamp.from(from), Timestamp.from(to), 5L, 10
                },
                argsCaptor.getValue()
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("omits every AND condition when all filter fields are null, keeping only the limit")
    void shouldOmitAndConditionsForNullFilterFields() {

        AuditEventFilter filter = new AuditEventFilter(
                null, null, null, null, null, null, null, 25
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        repository.findMatching(filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());

        assertFalse(sqlCaptor.getValue().contains("AND actor_id"));
        assertFalse(sqlCaptor.getValue().contains("AND resource_type"));
        assertFalse(sqlCaptor.getValue().contains("AND resource_id"));
        assertFalse(sqlCaptor.getValue().contains("AND event_type"));
        assertFalse(sqlCaptor.getValue().contains("AND event_timestamp"));
        assertFalse(sqlCaptor.getValue().contains("AND sequence_id"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY sequence_id ASC LIMIT ?"));

        assertArrayEquals(new Object[] { 25 }, argsCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("maps each result row back to an AuditEvent, deserializing the stored payload")
    void shouldMapResultSetRowsToAuditEvent() throws Exception {

        UUID eventId = UUID.randomUUID();
        AuditEventFilter filter = new AuditEventFilter(null, null, null, null, null, null, null, 50);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {

                    RowMapper<AuditEvent> rowMapper = invocation.getArgument(1);

                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("sequence_id")).thenReturn(7L);
                    when(resultSet.getObject("event_id", UUID.class)).thenReturn(eventId);
                    when(resultSet.getString("event_type")).thenReturn("USER_LOGIN");
                    when(resultSet.getString("actor_id")).thenReturn("user-1");
                    when(resultSet.getString("resource_type")).thenReturn("ACCOUNT");
                    when(resultSet.getString("resource_id")).thenReturn("A1");
                    when(resultSet.getString("integrity_payload")).thenReturn("{\"k\":\"v\"}");
                    when(resultSet.getTimestamp("event_timestamp"))
                            .thenReturn(Timestamp.from(Instant.parse("2026-09-17T10:15:30Z")));
                    when(resultSet.getInt("schema_version")).thenReturn(1);
                    when(resultSet.getString("content_hash")).thenReturn("content-hash");
                    when(resultSet.getString("previous_hash")).thenReturn("previous-hash");
                    when(resultSet.getString("chain_hash")).thenReturn("chain-hash");

                    when(jsonMapper.readValue("{\"k\":\"v\"}", Map.class))
                            .thenReturn(Map.of("k", "v"));

                    return List.of(rowMapper.mapRow(resultSet, 0));
                });

        List<AuditEvent> events = repository.findMatching(filter);

        assertEquals(1, events.size());
        AuditEvent event = events.get(0);

        assertEquals(7L, event.sequenceId());
        assertEquals(eventId, event.eventId());
        assertEquals("USER_LOGIN", event.eventType());
        assertEquals("user-1", event.actorId());
        assertEquals("ACCOUNT", event.resourceType());
        assertEquals("A1", event.resourceId());
        assertEquals(Map.of("k", "v"), event.payload());
        assertEquals(Instant.parse("2026-09-17T10:15:30Z"), event.eventTimestamp());
        assertEquals(1, event.schemaVersion());
        assertEquals("content-hash", event.contentHash());
        assertEquals("previous-hash", event.previousHash());
        assertEquals("chain-hash", event.chainHash());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("findAllByActorOrResource filters by actorId, ascending, including archived events")
    void shouldFindAllByActorIdIncludingArchivedEvents() {

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(String.class)))
                .thenReturn(List.of());

        repository.findAllByActorOrResource("user-1", null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("actor_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY sequence_id ASC"));
        assertFalse(sqlCaptor.getValue().contains("audit_event_archive"),
                "export must include archived events, unlike findMatching");
        assertEquals("user-1", argCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("findAllByActorOrResource filters by resourceId, ascending, including archived events")
    void shouldFindAllByResourceIdIncludingArchivedEvents() {

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(String.class)))
                .thenReturn(List.of());

        repository.findAllByActorOrResource(null, "A1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("resource_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY sequence_id ASC"));
        assertFalse(sqlCaptor.getValue().contains("audit_event_archive"));
        assertEquals("A1", argCaptor.getValue());
    }

    @Test
    @DisplayName("findAllByActorOrResource rejects both filters or neither filter")
    void shouldRejectBothOrNeitherFilterForExport() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findAllByActorOrResource(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> repository.findAllByActorOrResource("user-1", "A1"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("findAllForVerification reads every event, ascending, including archived events")
    void shouldFindAllForVerificationIncludingArchivedEvents() {

        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findAllForVerification();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));

        assertTrue(sqlCaptor.getValue().contains("ORDER BY sequence_id ASC"));
        assertFalse(sqlCaptor.getValue().contains("audit_event_archive"),
                "chain verification must read archived events too");
    }
}
