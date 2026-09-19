package com.audit.log.service;

import com.audit.log.api.ClientAccountAccessRequest;
import com.audit.log.api.CreateAuditEventRequest;
import com.audit.log.domain.AuditEvent;
import com.audit.log.persistence.AuditEventFilter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scenario C mapping and query boundary for client account access events. */
@Service
public class ClientAccountAccessService {
    public static final String EVENT_TYPE = "CLIENT_ACCOUNT_ACCESS";
    public static final String RESOURCE_TYPE = "CLIENT_ACCOUNT";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final AuditCommandService auditEvents;

    public ClientAccountAccessService(AuditCommandService auditEvents) {
        this.auditEvents = auditEvents;
    }

    public AuditEvent record(ClientAccountAccessRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", request.action().name());
        payload.put("outcome", request.outcome().name());
        payload.put("sourceApplication", request.sourceApplication());
        payload.put("requestId", request.requestId());
        payload.put("dataCategories", request.dataCategories());

        return auditEvents.create(new CreateAuditEventRequest(
                EVENT_TYPE,
                request.actorId(),
                RESOURCE_TYPE,
                request.accountId(),
                payload
        ));
    }

    public List<AuditEvent> find(String actorId, String accountId, Instant from, Instant to,
                                 Long afterSequenceId, Integer requestedLimit) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }

        int limit = requestedLimit == null || requestedLimit < 1
                ? DEFAULT_LIMIT
                : Math.min(requestedLimit, MAX_LIMIT);

        return auditEvents.findMatching(new AuditEventFilter(
                actorId,
                RESOURCE_TYPE,
                accountId,
                EVENT_TYPE,
                from,
                to,
                afterSequenceId,
                limit
        ));
    }
}
