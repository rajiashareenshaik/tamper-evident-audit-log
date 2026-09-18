package com.audit.log.service;

import com.audit.log.api.VerificationResponse;
import com.audit.log.domain.AuditEvent;
import com.audit.log.integrity.CanonicalAuditContent;
import com.audit.log.integrity.CanonicalJsonService;
import com.audit.log.integrity.HashService;
import com.audit.log.persistence.AuditEventRepository;
import com.audit.log.persistence.ChainHead;
import com.audit.log.persistence.ChainHeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChainVerificationService {
    private final AuditEventRepository events;
    private final ChainHeadRepository heads;
    private final CanonicalJsonService canonicalJson;
    private final HashService hashes;

    public ChainVerificationService(AuditEventRepository events, ChainHeadRepository heads,
                                    CanonicalJsonService canonicalJson, HashService hashes) {
        this.events = events;
        this.heads = heads;
        this.canonicalJson = canonicalJson;
        this.hashes = hashes;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VerificationResponse verify() {
        List<AuditEvent> all = events.findAllForVerification();
        String expectedPrevious = hashes.genesisHash();
        long verified = 0;
        for (AuditEvent event : all) {
            String canonical = canonicalJson.canonicalize(new CanonicalAuditContent(
                    event.eventId(), event.eventType(), event.actorId(), event.resourceType(),
                    event.resourceId(), event.payload(), event.eventTimestamp(), event.schemaVersion()));
            String content = hashes.sha256(canonical);
            if (!content.equals(event.contentHash()))
                return VerificationResponse.broken(verified, event.sequenceId(), event.eventId(), "CONTENT_HASH_MISMATCH");
            if (!expectedPrevious.equals(event.previousHash()))
                return VerificationResponse.broken(verified, event.sequenceId(), event.eventId(), "PREVIOUS_HASH_MISMATCH");
            if (!hashes.chainHash(event.previousHash(), event.contentHash()).equals(event.chainHash()))
                return VerificationResponse.broken(verified, event.sequenceId(), event.eventId(), "CHAIN_HASH_MISMATCH");
            expectedPrevious = event.chainHash();
            verified++;
        }

        ChainHead head = heads.findGlobal();
        if (head == null)
            return all.isEmpty() ? VerificationResponse.intact(0)
                    : VerificationResponse.broken(verified, lastSequence(all), lastEventId(all), "CHAIN_HEAD_MISMATCH");
        if (head.eventCount() != all.size())
            return VerificationResponse.broken(verified, lastSequence(all), lastEventId(all), "CHAIN_COUNT_MISMATCH");
        if (!expectedPrevious.equals(head.lastChainHash()) || !equal(lastEventId(all), head.lastEventId())
                || !equal(all.isEmpty() ? null : lastSequence(all), head.lastSequenceId()))
            return VerificationResponse.broken(verified, lastSequence(all), lastEventId(all), "CHAIN_HEAD_MISMATCH");
        return VerificationResponse.intact(verified);
    }

    private long lastSequence(List<AuditEvent> all) { return all.isEmpty() ? 0 : all.get(all.size() - 1).sequenceId(); }
    private java.util.UUID lastEventId(List<AuditEvent> all) { return all.isEmpty() ? null : all.get(all.size() - 1).eventId(); }
    private boolean equal(Object left, Object right) { return java.util.Objects.equals(left, right); }
}
