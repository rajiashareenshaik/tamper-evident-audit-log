package com.audit.log.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
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

    @Test void reportsZeroWhenNoEventsAreEligible() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);
        var result = new RetentionService(jdbc, Clock.systemUTC(), Duration.ofDays(30)).archiveExpired();
        assertEquals(0, result.archivedCount());
    }

    @Test void usesAStrictCutoffBoundaryOfNowMinusWindow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        Duration window = Duration.ofDays(30);
        Instant expectedCutoff = now.minus(window);

        new RetentionService(jdbc, Clock.fixed(now, ZoneOffset.UTC), window).archiveExpired();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Timestamp> archivedAtCaptor = ArgumentCaptor.forClass(Timestamp.class);
        ArgumentCaptor<Timestamp> policyCutoffCaptor = ArgumentCaptor.forClass(Timestamp.class);
        ArgumentCaptor<Timestamp> whereCutoffCaptor = ArgumentCaptor.forClass(Timestamp.class);

        verify(jdbc).update(sqlCaptor.capture(), archivedAtCaptor.capture(),
                policyCutoffCaptor.capture(), whereCutoffCaptor.capture());

        // The eligibility test must be strict ("<"), not inclusive ("<="): an event exactly
        // `window` old is retained for one more instant, not archived immediately.
        assertTrue(sqlCaptor.getValue().contains("WHERE event_timestamp < ?"));
        assertEquals(Timestamp.from(now), archivedAtCaptor.getValue());
        assertEquals(Timestamp.from(expectedCutoff), policyCutoffCaptor.getValue());
        assertEquals(Timestamp.from(expectedCutoff), whereCutoffCaptor.getValue());
    }

    @Test void reportsZeroOnASecondRunOverTheSameData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Postgres's "INSERT ... ON CONFLICT DO NOTHING" row count reflects only newly inserted
        // rows, so a second run over already-archived data is expected to report zero.
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(3).thenReturn(0);

        RetentionService service = new RetentionService(jdbc, Clock.systemUTC(), Duration.ofDays(30));

        assertEquals(3, service.archiveExpired().archivedCount());
        assertEquals(0, service.archiveExpired().archivedCount());
    }
}
