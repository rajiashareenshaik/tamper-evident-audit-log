package com.audit.log.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetentionServiceTest {
    @Test void archivesOnlyOnceAndReportsThePolicyCutoff() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(3);
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        var result = new RetentionService(jdbc, Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(30)).archiveExpired();
        assertEquals(3, result.archivedCount());
        assertEquals(now.minus(Duration.ofDays(30)), result.cutoff());
        verify(jdbc).update(contains("ON CONFLICT (event_id) DO NOTHING"), any(), any(), any());
    }

    @Test void rejectsAZeroWindow() {
        assertThrows(IllegalArgumentException.class, () ->
                new RetentionService(mock(JdbcTemplate.class), Clock.systemUTC(), Duration.ZERO));
    }
}
