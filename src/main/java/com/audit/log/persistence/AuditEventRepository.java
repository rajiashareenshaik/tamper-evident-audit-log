package com.audit.log.persistence;

import com.audit.log.domain.AuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence for {@code audit_event}: append-only writes plus filtered reads. Exposes no update
 * or delete operations, and holds no transaction or chain-locking logic — that belongs to the
 * caller (see {@code AuditCommandService}).
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

    private static final String SELECT_SQL = """
            SELECT
                sequence_id,
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
            FROM audit_event
            WHERE 1 = 1
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
     * Finds audit events matching the given filter, ordered by sequence_id ascending and
     * cursor-paginated via {@link AuditEventFilter#afterSequenceId()}.
     *
     * @param filter fields to filter by; a {@code null} field is not applied as a filter
     * @return matching events, oldest first, up to {@link AuditEventFilter#limit()} of them
     */
    public List<AuditEvent> findMatching(AuditEventFilter filter) {

        StringBuilder sql = new StringBuilder(SELECT_SQL);
        List<Object> args = new ArrayList<>();

        if (filter.actorId() != null) {
            sql.append(" AND actor_id = ?");
            args.add(filter.actorId());
        }

        if (filter.resourceType() != null) {
            sql.append(" AND resource_type = ?");
            args.add(filter.resourceType());
        }

        if (filter.resourceId() != null) {
            sql.append(" AND resource_id = ?");
            args.add(filter.resourceId());
        }

        if (filter.eventType() != null) {
            sql.append(" AND event_type = ?");
            args.add(filter.eventType());
        }

        if (filter.from() != null) {
            sql.append(" AND event_timestamp >= ?");
            args.add(Timestamp.from(filter.from()));
        }

        if (filter.to() != null) {
            sql.append(" AND event_timestamp <= ?");
            args.add(Timestamp.from(filter.to()));
        }

        if (filter.afterSequenceId() != null) {
            sql.append(" AND sequence_id > ?");
            args.add(filter.afterSequenceId());
        }

        sql.append(" ORDER BY sequence_id ASC LIMIT ?");
        args.add(filter.limit());

        return jdbcTemplate.query(sql.toString(), rowMapper(), args.toArray());
    }

    private RowMapper<AuditEvent> rowMapper() {
        return (rs, rowNum) -> new AuditEvent(
                rs.getLong("sequence_id"),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("actor_id"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                deserializePayload(rs.getString("integrity_payload")),
                rs.getTimestamp("event_timestamp").toInstant(),
                rs.getInt("schema_version"),
                rs.getString("content_hash"),
                rs.getString("previous_hash"),
                rs.getString("chain_hash")
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

    /**
     * @return the {@code integrity_payload} column's JSON, parsed back into a map
     * @throws IllegalStateException if the stored payload can't be deserialized
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializePayload(String payloadJson) {
        try {
            return jsonMapper.readValue(payloadJson, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to deserialize audit event payload",
                    e
            );
        }
    }
}
