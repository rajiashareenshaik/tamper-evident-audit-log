package com.audit.log.service;

import com.audit.log.api.CreateAuditEventRequest;
import com.audit.log.domain.AuditEvent;
import com.audit.log.integrity.CanonicalAuditContent;
import com.audit.log.integrity.CanonicalJsonService;
import com.audit.log.integrity.HashService;
import com.audit.log.persistence.AuditEventRepository;
import com.audit.log.persistence.ChainHead;
import com.audit.log.persistence.ChainHeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditCommandService")
class AuditCommandServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-17T10:15:30Z");

    @Mock
    private ChainHeadRepository chainHeadRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private CanonicalJsonService canonicalJsonService;

    @Mock
    private HashService hashService;

    private AuditCommandService service;

    @BeforeEach
    void setUp() {
        service = new AuditCommandService(
                chainHeadRepository,
                auditEventRepository,
                canonicalJsonService,
                hashService,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("appends the new event using the current chain tip as previousHash, not genesis")
    void shouldChainOffCurrentTipRatherThanGenesis() {

        CreateAuditEventRequest request = new CreateAuditEventRequest(
                "USER_LOGIN",
                "user-1",
                "ACCOUNT",
                "A1",
                Map.of("k", "v")
        );

        ChainHead chainHead = new ChainHead(
                "GLOBAL",
                10L,
                UUID.randomUUID(),
                "previous-chain-hash",
                10L
        );

        when(hashService.genesisHash()).thenReturn("genesis-hash");
        when(chainHeadRepository.lockOrCreate("genesis-hash")).thenReturn(chainHead);
        when(canonicalJsonService.canonicalize(any(CanonicalAuditContent.class)))
                .thenReturn("canonical-json");
        when(hashService.sha256("canonical-json")).thenReturn("content-hash");
        when(hashService.chainHash("previous-chain-hash", "content-hash"))
                .thenReturn("new-chain-hash");
        when(auditEventRepository.insert(any(AuditEvent.class))).thenReturn(11L);

        AuditEvent result = service.create(request);

        ArgumentCaptor<AuditEvent> insertedCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).insert(insertedCaptor.capture());
        AuditEvent inserted = insertedCaptor.getValue();

        assertEquals("previous-chain-hash", inserted.previousHash());
        assertEquals("content-hash", inserted.contentHash());
        assertEquals("new-chain-hash", inserted.chainHash());
        assertEquals(FIXED_INSTANT, inserted.eventTimestamp());

        verify(chainHeadRepository).update(11L, inserted.eventId(), "new-chain-hash", 11L);

        assertEquals(11L, result.sequenceId());
        assertEquals(inserted.eventId(), result.eventId());
        assertEquals("previous-chain-hash", result.previousHash());
        assertEquals("content-hash", result.contentHash());
        assertEquals("new-chain-hash", result.chainHash());
        assertEquals(request.eventType(), result.eventType());
        assertEquals(request.actorId(), result.actorId());
        assertEquals(request.resourceType(), result.resourceType());
        assertEquals(request.resourceId(), result.resourceId());
        assertEquals(request.payload(), result.payload());
    }

    @Test
    @DisplayName("leaves the chain head untouched when the event insert fails")
    void shouldNotAdvanceChainHeadWhenInsertFails() {

        CreateAuditEventRequest request = new CreateAuditEventRequest(
                "USER_LOGIN",
                "user-1",
                "ACCOUNT",
                "A1",
                Map.of("k", "v")
        );

        ChainHead chainHead = new ChainHead(
                "GLOBAL",
                10L,
                UUID.randomUUID(),
                "previous-chain-hash",
                10L
        );

        when(hashService.genesisHash()).thenReturn("genesis-hash");
        when(chainHeadRepository.lockOrCreate("genesis-hash")).thenReturn(chainHead);
        when(canonicalJsonService.canonicalize(any(CanonicalAuditContent.class)))
                .thenReturn("canonical-json");
        when(hashService.sha256("canonical-json")).thenReturn("content-hash");
        when(hashService.chainHash("previous-chain-hash", "content-hash"))
                .thenReturn("new-chain-hash");
        when(auditEventRepository.insert(any(AuditEvent.class)))
                .thenThrow(new RuntimeException("insert failed"));

        try {
            service.create(request);
        } catch (RuntimeException expected) {
            // expected
        }

        verify(chainHeadRepository, never()).update(
                org.mockito.ArgumentMatchers.anyLong(),
                any(UUID.class),
                any(String.class),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }
}
