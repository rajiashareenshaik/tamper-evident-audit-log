package com.audit.log.api;

import com.audit.log.domain.AuditEvent;
import com.audit.log.service.AuditCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditWriteController")
class AuditWriteControllerTest {

    @Mock
    private AuditCommandService auditCommandService;

    @Test
    @DisplayName("returns 201 with the persisted event mapped to a response body")
    void shouldReturnCreatedWithPersistedEvent() {

        AuditWriteController controller = new AuditWriteController(auditCommandService);

        CreateAuditEventRequest request = new CreateAuditEventRequest(
                "USER_LOGIN",
                "user-1",
                "ACCOUNT",
                "A1",
                Map.of("k", "v")
        );

        AuditEvent persisted = new AuditEvent(
                1L,
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

        when(auditCommandService.create(request)).thenReturn(persisted);

        ResponseEntity<AuditEventResponse> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(AuditEventResponse.from(persisted), response.getBody());
    }
}
