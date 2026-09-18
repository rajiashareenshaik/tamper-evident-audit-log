package com.audit.log.api;

import com.audit.log.domain.AuditEvent;
import com.audit.log.persistence.AuditEventFilter;
import com.audit.log.service.AuditCommandService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * HTTP API for appending to and querying the global audit hash chain.
 */
@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final AuditCommandService auditCommandService;

    public AuditController(AuditCommandService auditCommandService) {
        this.auditCommandService = auditCommandService;
    }

    /**
     * Records a new audit event onto the global chain.
     *
     * @param request the event to record
     * @return 201 with the persisted event, including its server-assigned sequenceId and hashes
     */
    @PostMapping
    public ResponseEntity<AuditEventResponse> create(
            @Valid @RequestBody CreateAuditEventRequest request
    ) {
        AuditEvent event = auditCommandService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuditEventResponse.from(event));
    }

    /**
     * Finds audit events matching the given filters, oldest first, cursor-paginated by
     * sequence_id.
     *
     * @param afterSequenceId cursor: only return events after this sequence_id
     * @param limit page size; defaults to {@value #DEFAULT_LIMIT}, capped at {@value #MAX_LIMIT}
     * @return matching events, oldest first
     */
    @GetMapping
    public List<AuditEventResponse> findEvents(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Long afterSequenceId,
            @RequestParam(required = false) Integer limit
    ) {
        AuditEventFilter filter = new AuditEventFilter(
                actorId,
                resourceType,
                resourceId,
                eventType,
                from,
                to,
                afterSequenceId,
                clampLimit(limit)
        );

        return auditCommandService.findMatching(filter).stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    private int clampLimit(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
