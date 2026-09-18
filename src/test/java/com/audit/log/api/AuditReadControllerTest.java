package com.audit.log.api;

import com.audit.log.domain.AuditEvent;
import com.audit.log.persistence.AuditEventFilter;
import com.audit.log.persistence.AuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditReadController")
class AuditReadControllerTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Test
    @DisplayName("builds a filter from every query param and maps results to responses")
    void shouldBuildFilterFromRequestParamsAndMapResults() {

        AuditReadController controller = new AuditReadController(auditEventRepository);

        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-30T00:00:00Z");

        AuditEvent event = new AuditEvent(
                7L,
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

        when(auditEventRepository.findMatching(any(AuditEventFilter.class)))
                .thenReturn(List.of(event));

        List<AuditEventResponse> responses = controller.findEvents(
                "user-1", "ACCOUNT", "A1", "USER_LOGIN", from, to, 5L, 10
        );

        ArgumentCaptor<AuditEventFilter> filterCaptor = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(auditEventRepository).findMatching(filterCaptor.capture());
        AuditEventFilter filter = filterCaptor.getValue();

        assertEquals("user-1", filter.actorId());
        assertEquals("ACCOUNT", filter.resourceType());
        assertEquals("A1", filter.resourceId());
        assertEquals("USER_LOGIN", filter.eventType());
        assertEquals(from, filter.from());
        assertEquals(to, filter.to());
        assertEquals(5L, filter.afterSequenceId());
        assertEquals(10, filter.limit());

        assertEquals(List.of(AuditEventResponse.from(event)), responses);
    }

    @Test
    @DisplayName("defaults the limit to 50 when none is given")
    void shouldDefaultLimitWhenNotProvided() {

        AuditReadController controller = new AuditReadController(auditEventRepository);

        when(auditEventRepository.findMatching(any(AuditEventFilter.class))).thenReturn(List.of());

        controller.findEvents(null, null, null, null, null, null, null, null);

        ArgumentCaptor<AuditEventFilter> filterCaptor = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(auditEventRepository).findMatching(filterCaptor.capture());

        assertEquals(50, filterCaptor.getValue().limit());
    }

    @Test
    @DisplayName("defaults the limit to 50 when a non-positive value is given")
    void shouldDefaultLimitWhenNonPositive() {

        AuditReadController controller = new AuditReadController(auditEventRepository);

        when(auditEventRepository.findMatching(any(AuditEventFilter.class))).thenReturn(List.of());

        controller.findEvents(null, null, null, null, null, null, null, 0);

        ArgumentCaptor<AuditEventFilter> filterCaptor = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(auditEventRepository).findMatching(filterCaptor.capture());

        assertEquals(50, filterCaptor.getValue().limit());
    }

    @Test
    @DisplayName("caps the limit at 500 when a larger value is given")
    void shouldCapLimitAtMax() {

        AuditReadController controller = new AuditReadController(auditEventRepository);

        when(auditEventRepository.findMatching(any(AuditEventFilter.class))).thenReturn(List.of());

        controller.findEvents(null, null, null, null, null, null, null, 10_000);

        ArgumentCaptor<AuditEventFilter> filterCaptor = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(auditEventRepository).findMatching(filterCaptor.capture());

        assertEquals(500, filterCaptor.getValue().limit());
    }
}
