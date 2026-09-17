package com.audit.log.persistence;

import com.audit.log.domain.AuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;

/**
 * Append-only persistence for {@code audit_event}. Intentionally exposes no update or delete
 * operations, and holds no transaction or chain-locking logic — that belongs to the caller
 * (see {@code AuditCommandService}).
 */
@Repository
public class AuditEventRepository {

    private static final String INSERT_SQL = """
            INSERT INTO audit_event (
                event_id,
                event_type,
                actor_id,
                resource_type,
                resource_id,
                integrity_payload,
                event_timestamp,
                schema_version,
                content_hash,
                previous_hash,
                chain_hash
            )
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
            RETURNING sequence_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public AuditEventRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Inserts a single, already hash-chained audit event.
     * {@code sequence_id} and {@code created_at} are assigned by PostgreSQL.
     *
     * @return the sequence_id generated for this row
     */
    public long insert(AuditEvent event) {

        String payloadJson = serializePayload(event);

        return jdbcTemplate.queryForObject(
                INSERT_SQL,
                Long.class,
                event.eventId(),
                event.eventType(),
                event.actorId(),
                event.resourceType(),
                event.resourceId(),
                payloadJson,
                Timestamp.from(event.eventTimestamp()),
                event.schemaVersion(),
                event.contentHash(),
                event.previousHash(),
                event.chainHash()
        );
    }

    /**
     * @return {@code event.payload()} as a JSON string, ready to bind to a {@code jsonb} column
     * @throws IllegalStateException if the payload can't be serialized to JSON
     */
    private String serializePayload(AuditEvent event) {
        try {
            return jsonMapper.writeValueAsString(event.payload());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to serialize audit event payload for event " + event.eventId(),
                    e
            );
        }
    }
}
