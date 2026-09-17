package com.audit.log.api;

import com.audit.log.domain.AuditEvent;
import com.audit.log.service.AuditCommandService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Write-side HTTP API for appending events to the global audit hash chain.
 */
@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditWriteController {

    private final AuditCommandService auditCommandService;

    public AuditWriteController(AuditCommandService auditCommandService) {
        this.auditCommandService = auditCommandService;
    }

    /**
     * Records a new audit event onto the global chain.
     *
     * @param request the event to record
     * @param idempotencyKey optional caller-supplied key to dedupe retried submissions
     * @return 201 with the persisted event, including its server-assigned sequenceId and hashes
     */
    @PostMapping
    public ResponseEntity<AuditEventResponse> create(
            @Valid @RequestBody CreateAuditEventRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        AuditEvent event = auditCommandService.record(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuditEventResponse.from(event));
    }
}
