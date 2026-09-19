package com.audit.log.api;

import com.audit.log.domain.AuditEvent;
import com.audit.log.service.ClientAccountAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Client account access controller")
class ClientAccountAccessControllerTest {
    @Mock
    private ClientAccountAccessService service;

    @Test
    @DisplayName("returns the persisted access event with created status")
    void recordsAccessEvent() {
        ClientAccountAccessRequest request = new ClientAccountAccessRequest(
                "employee-123", "account-456", ClientAccountAccessRequest.Action.READ,
                ClientAccountAccessRequest.Outcome.ALLOWED, "CSR_PORTAL", "request-789",
                List.of("CONTACT_DETAILS"));
        AuditEvent event = event();
        when(service.record(request)).thenReturn(event);

        ResponseEntity<AuditEventResponse> response =
                new ClientAccountAccessController(service).record(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(AuditEventResponse.from(event), response.getBody());
    }

    @Test
    @DisplayName("maps compliance report results to API responses")
    void findsAccessEvents() {
        AuditEvent event = event();
        when(service.find(null, "account-456", null, null, null, 25))
                .thenReturn(List.of(event));

        List<AuditEventResponse> response = new ClientAccountAccessController(service)
                .find(null, "account-456", null, null, null, 25);

        assertEquals(List.of(AuditEventResponse.from(event)), response);
    }

    private AuditEvent event() {
        return new AuditEvent(
                1L, UUID.randomUUID(), "CLIENT_ACCOUNT_ACCESS", "employee-123",
                "CLIENT_ACCOUNT", "account-456", Map.of("action", "READ"),
                Instant.parse("2026-09-19T20:00:00Z"), 1,
                "content-hash", "previous-hash", "chain-hash");
    }
}
