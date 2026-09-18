package com.audit.log.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class RetentionService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration window;

    public RetentionService(JdbcTemplate jdbc, Clock clock,
                            @Value("${audit.retention-window:P365D}") Duration window) {
        if (window.isNegative() || window.isZero()) throw new IllegalArgumentException("Retention window must be positive");
        this.jdbc = jdbc;
        this.clock = clock;
        this.window = window;
    }

    @Transactional
    public ArchiveResult archiveExpired() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        int count = jdbc.update("""
                INSERT INTO audit_event_archive(event_id, archived_at, policy_cutoff)
                SELECT event_id, ?, ? FROM audit_event
                WHERE event_timestamp < ?
                ON CONFLICT (event_id) DO NOTHING
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(cutoff), java.sql.Timestamp.from(cutoff));
        return new ArchiveResult(count, cutoff, now);
    }

    public record ArchiveResult(int archivedCount, Instant cutoff, Instant archivedAt) { }
}
