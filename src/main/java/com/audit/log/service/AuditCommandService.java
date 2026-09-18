package com.audit.log.service;

import com.audit.log.api.CreateAuditEventRequest;
import com.audit.log.domain.AuditEvent;
import com.audit.log.integrity.CanonicalAuditContent;
import com.audit.log.integrity.CanonicalJsonService;
import com.audit.log.integrity.HashService;
import com.audit.log.persistence.AuditEventFilter;
import com.audit.log.persistence.AuditEventRepository;
import com.audit.log.persistence.ChainHead;
import com.audit.log.persistence.ChainHeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns reads and writes on the single global audit hash chain. Owns the transaction and locking
 * boundary for the append operation; the repositories it calls hold none of their own.
 */
@Service
public class AuditCommandService {

    private static final int SCHEMA_VERSION = 1;
    private final ChainHeadRepository chainHeadRepository;
    private final AuditEventRepository auditEventRepository;
    private final CanonicalJsonService canonicalJsonService;
    private final HashService hashService;
    private final Clock clock;

    public AuditCommandService(
            ChainHeadRepository chainHeadRepository,
            AuditEventRepository auditEventRepository,
            CanonicalJsonService canonicalJsonService,
            HashService hashService,
            Clock clock
    ) {
        this.chainHeadRepository = chainHeadRepository;
        this.auditEventRepository = auditEventRepository;
        this.canonicalJsonService = canonicalJsonService;
        this.hashService = hashService;
        this.clock = clock;
    }

    /**
     * Appends one new event to the global chain: locks the chain head, assigns the event's id
     * and server timestamp, computes its content and chain hashes off the current tip, inserts
     * it, and advances the chain head — all within a single transaction, so a failure at any step
     * leaves neither the event row nor the chain head changed.
     *
     * @param request the caller-supplied event to record
     * @return the persisted event, including its server-assigned sequenceId and hashes
     */
    @Transactional
    public AuditEvent create(CreateAuditEventRequest request) {

        ChainHead chainHead = chainHeadRepository.lockOrCreate(hashService.genesisHash());

        UUID eventId = UUID.randomUUID();
        Instant eventTimestamp = clock.instant();

        CanonicalAuditContent canonicalContent = new CanonicalAuditContent(
                eventId,
                request.eventType(),
                request.actorId(),
                request.resourceType(),
                request.resourceId(),
                request.payload(),
                eventTimestamp,
                SCHEMA_VERSION
        );

        String canonicalJson = canonicalJsonService.canonicalize(canonicalContent);
        String contentHash = hashService.sha256(canonicalJson);
        String previousHash = chainHead.lastChainHash();
        String chainHash = hashService.chainHash(previousHash, contentHash);

        AuditEvent eventToInsert = new AuditEvent(
                0L,
                eventId,
                request.eventType(),
                request.actorId(),
                request.resourceType(),
                request.resourceId(),
                request.payload(),
                eventTimestamp,
                SCHEMA_VERSION,
                contentHash,
                previousHash,
                chainHash
        );

        long sequenceId = auditEventRepository.insert(eventToInsert);

        chainHeadRepository.update(
                sequenceId,
                eventId,
                chainHash,
                chainHead.eventCount() + 1
        );

        return new AuditEvent(
                sequenceId,
                eventId,
                request.eventType(),
                request.actorId(),
                request.resourceType(),
                request.resourceId(),
                request.payload(),
                eventTimestamp,
                SCHEMA_VERSION,
                contentHash,
                previousHash,
                chainHash
        );
    }

    /**
     * Finds audit events matching the given filter.
     *
     * @param filter fields to filter by; a {@code null} field is not applied as a filter
     * @return matching events, oldest first, up to {@link AuditEventFilter#limit()} of them
     */
    public List<AuditEvent> findMatching(AuditEventFilter filter) {
        return auditEventRepository.findMatching(filter);
    }
}
