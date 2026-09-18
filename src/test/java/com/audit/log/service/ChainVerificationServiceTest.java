package com.audit.log.service;

import com.audit.log.domain.AuditEvent;
import com.audit.log.integrity.CanonicalJsonService;
import com.audit.log.integrity.HashService;
import com.audit.log.persistence.AuditEventRepository;
import com.audit.log.persistence.ChainHead;
import com.audit.log.persistence.ChainHeadRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChainVerificationServiceTest {
    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final ChainHeadRepository heads = mock(ChainHeadRepository.class);
    private final HashService hashes = new HashService();
    private final CanonicalJsonService canonical = new CanonicalJsonService(JsonMapper.builder().build());
    private final ChainVerificationService service = new ChainVerificationService(events, heads, canonical, hashes);

    @Test void acceptsAnEmptyUninitializedChain() {
        when(events.findAllForVerification()).thenReturn(List.of());
        when(heads.findGlobal()).thenReturn(null);
        assertTrue(service.verify().intact());
    }

    @Test void reportsTheFirstContentChange() {
        AuditEvent valid = event(1, "one", hashes.genesisHash());
        AuditEvent changed = new AuditEvent(valid.sequenceId(), valid.eventId(), valid.eventType(), valid.actorId(),
                valid.resourceType(), valid.resourceId(), Map.of("message", "changed"), valid.eventTimestamp(),
                valid.schemaVersion(), valid.contentHash(), valid.previousHash(), valid.chainHash());
        when(events.findAllForVerification()).thenReturn(List.of(changed));
        when(heads.findGlobal()).thenReturn(new ChainHead("GLOBAL", 1L, valid.eventId(), valid.chainHash(), 1));
        var result = service.verify();
        assertFalse(result.intact());
        assertEquals("CONTENT_HASH_MISMATCH", result.firstInconsistency().violationType());
        assertEquals(1, result.firstInconsistency().sequence());
    }

    private AuditEvent event(long sequence, String message, String previous) {
        UUID id = UUID.randomUUID(); Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        var content = new com.audit.log.integrity.CanonicalAuditContent(id, "TEST", "actor", "TYPE", "id",
                Map.of("message", message), timestamp, 1);
        String contentHash = hashes.sha256(canonical.canonicalize(content));
        return new AuditEvent(sequence, id, "TEST", "actor", "TYPE", "id", Map.of("message", message),
                timestamp, 1, contentHash, previous, hashes.chainHash(previous, contentHash));
    }
}
