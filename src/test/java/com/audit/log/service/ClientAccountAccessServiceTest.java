package com.audit.log.service;

import com.audit.log.api.ClientAccountAccessRequest;
import com.audit.log.api.CreateAuditEventRequest;
import com.audit.log.domain.AuditEvent;
import com.audit.log.persistence.AuditEventFilter;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Client account access service")
class ClientAccountAccessServiceTest {
    @Mock
    private AuditCommandService auditEvents;

    @Test
    @DisplayName("maps the fixed compliance contract into a general audit event")
    void mapsAccessEvent() {
        ClientAccountAccessRequest access = request(ClientAccountAccessRequest.Outcome.ALLOWED);
        AuditEvent persisted = new AuditEvent(
                1L, UUID.randomUUID(), "CLIENT_ACCOUNT_ACCESS", "employee-123",
                "CLIENT_ACCOUNT", "account-456", Map.of("action", "READ"),
                Instant.parse("2026-09-19T20:00:00Z"), 1,
                "content-hash", "previous-hash", "chain-hash");
        when(auditEvents.create(any())).thenReturn(persisted);

        ClientAccountAccessService service = new ClientAccountAccessService(auditEvents);
        assertEquals(persisted, service.record(access));

        ArgumentCaptor<CreateAuditEventRequest> captured =
                ArgumentCaptor.forClass(CreateAuditEventRequest.class);
        verify(auditEvents).create(captured.capture());
        CreateAuditEventRequest event = captured.getValue();
        assertEquals("CLIENT_ACCOUNT_ACCESS", event.eventType());
        assertEquals("CLIENT_ACCOUNT", event.resourceType());
        assertEquals("employee-123", event.actorId());
        assertEquals("account-456", event.resourceId());
        assertEquals("READ", event.payload().get("action"));
        assertEquals("ALLOWED", event.payload().get("outcome"));
        assertEquals("CSR_PORTAL", event.payload().get("sourceApplication"));
        assertEquals("request-789", event.payload().get("requestId"));
        assertEquals(List.of("CONTACT_DETAILS"), event.payload().get("dataCategories"));
        assertEquals(5, event.payload().size());
    }

    @Test
    @DisplayName("uses fixed compliance filters and caller supplied report filters")
    void buildsComplianceFilter() {
        when(auditEvents.findMatching(any())).thenReturn(List.of());
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-30T00:00:00Z");
        ClientAccountAccessService service = new ClientAccountAccessService(auditEvents);

        service.find("employee-123", "account-456", from, to, 10L, 1000);

        ArgumentCaptor<AuditEventFilter> captured = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(auditEvents).findMatching(captured.capture());
        AuditEventFilter filter = captured.getValue();
        assertEquals("employee-123", filter.actorId());
        assertEquals("account-456", filter.resourceId());
        assertEquals("CLIENT_ACCOUNT", filter.resourceType());
        assertEquals("CLIENT_ACCOUNT_ACCESS", filter.eventType());
        assertEquals(from, filter.from());
        assertEquals(to, filter.to());
        assertEquals(10L, filter.afterSequenceId());
        assertEquals(500, filter.limit());
    }

    @Test
    @DisplayName("rejects an inverted reporting period")
    void rejectsInvertedPeriod() {
        ClientAccountAccessService service = new ClientAccountAccessService(auditEvents);
        Instant from = Instant.parse("2026-09-30T00:00:00Z");
        Instant to = Instant.parse("2026-09-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> service.find(null, null, from, to, null, null));
    }

    private ClientAccountAccessRequest request(ClientAccountAccessRequest.Outcome outcome) {
        return new ClientAccountAccessRequest(
                "employee-123", "account-456", ClientAccountAccessRequest.Action.READ,
                outcome, "CSR_PORTAL", "request-789", List.of("CONTACT_DETAILS"));
    }
}
