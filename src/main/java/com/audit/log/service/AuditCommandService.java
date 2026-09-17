package com.audit.log.service;

import com.audit.log.api.CreateAuditEventRequest;
import com.audit.log.domain.AuditEvent;
import com.audit.log.integrity.CanonicalAuditContent;
import com.audit.log.integrity.CanonicalJsonService;
import com.audit.log.integrity.HashService;
import com.audit.log.persistence.ChainHead;
import com.audit.log.persistence.ChainHeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes new events onto the single global audit hash chain. Owns the transaction and locking
 * boundary for the whole append operation; the repositories it calls hold none of their own.
 */
@Service
public class AuditCommandService {

    private static final int SCHEMA_VERSION = 1;
    private final CanonicalJsonService canonicalJsonService;
    private final HashService hashService;
    private final Clock clock;

    public AuditCommandService(
            CanonicalJsonService canonicalJsonService,
            HashService hashService,
            Clock clock
    ) {
        this.canonicalJsonService = canonicalJsonService;
        this.hashService = hashService;
        this.clock = clock;
    }

    /**
     * Builds a hashed audit event from the given request. Does not persist anything or consult
     * the chain's tip, so {@code previousHash} is always the genesis hash for now.
     *
     * @param request the caller-supplied event to record
     * @return the hashed event, with a placeholder sequenceId of {@code 0}
     */
    public AuditEvent create(CreateAuditEventRequest request) {

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
        String previousHash = hashService.genesisHash();
        String chainHash = hashService.chainHash(previousHash, contentHash);

        return new AuditEvent(
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
    }
}
