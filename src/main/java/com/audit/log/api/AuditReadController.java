package com.audit.log.api;

import com.audit.log.persistence.AuditEventFilter;
import com.audit.log.persistence.AuditEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Read-side HTTP API for querying events on the global audit hash chain.
 */
@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditReadController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final AuditEventRepository auditEventRepository;

    public AuditReadController(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
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

        return auditEventRepository.findMatching(filter).stream()
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
