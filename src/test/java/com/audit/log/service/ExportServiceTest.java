package com.audit.log.service;

import com.audit.log.domain.AuditEvent;
import com.audit.log.integrity.HashService;
import com.audit.log.persistence.AuditEventRepository;
import com.audit.log.persistence.ChainHead;
import com.audit.log.persistence.ChainHeadRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportServiceTest {

    private final HashService hashService = new HashService();

    @Test void createsAnIndependentlyVerifiableSignedManifest() throws Exception {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        when(repository.findAllByActorOrResource("actor-1", null)).thenReturn(List.of());
        when(chainHeads.findGlobal()).thenReturn(
                new ChainHead("GLOBAL", 5L, UUID.randomUUID(), "some-chain-hash", 5));

        var bundle = new ExportService(repository, chainHeads, hashService, JsonMapper.builder().build(), Clock.systemUTC())
                .export("actor-1", null);

        var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(bundle.manifest().publicKey())));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(bundle.manifest().bundleDigest().getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(bundle.manifest().signature())));
    }

    @Test void requiresExactlyOneFilter() {
        var service = new ExportService(mock(AuditEventRepository.class), mock(ChainHeadRepository.class),
                hashService, JsonMapper.builder().build(), Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> service.export(null, null));
        assertThrows(IllegalArgumentException.class, () -> service.export("a", "r"));
    }

    @Test void anchorsToTheCurrentChainHeadWhenTheChainIsNotEmpty() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        when(repository.findAllByActorOrResource(null, "resource-1")).thenReturn(List.of());
        when(chainHeads.findGlobal()).thenReturn(
                new ChainHead("GLOBAL", 42L, UUID.randomUUID(), "head-chain-hash", 42));

        var bundle = new ExportService(repository, chainHeads, hashService, JsonMapper.builder().build(), Clock.systemUTC())
                .export(null, "resource-1");

        assertEquals(hashService.genesisHash(), bundle.chainAnchor().genesisHash());
        assertEquals("head-chain-hash", bundle.chainAnchor().chainHeadHash());
        assertEquals(42, bundle.chainAnchor().chainEventCount());
        assertEquals(42L, bundle.chainAnchor().chainLastSequenceId());
    }

    @Test void anchorsToGenesisWhenTheChainHasNeverBeenSeeded() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        when(repository.findAllByActorOrResource("actor-1", null)).thenReturn(List.of());
        when(chainHeads.findGlobal()).thenReturn(null);

        var bundle = new ExportService(repository, chainHeads, hashService, JsonMapper.builder().build(), Clock.systemUTC())
                .export("actor-1", null);

        assertEquals(hashService.genesisHash(), bundle.chainAnchor().genesisHash());
        assertEquals(hashService.genesisHash(), bundle.chainAnchor().chainHeadHash());
        assertEquals(0, bundle.chainAnchor().chainEventCount());
        assertNull(bundle.chainAnchor().chainLastSequenceId());
    }

    @Test void exportsMatchingRecordsByActorId() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        AuditEvent first = sampleEvent(1L, "actor-1", "ACCOUNT", "A1");
        AuditEvent second = sampleEvent(2L, "actor-1", "ACCOUNT", "A1");
        when(repository.findAllByActorOrResource("actor-1", null)).thenReturn(List.of(first, second));
        when(chainHeads.findGlobal()).thenReturn(new ChainHead("GLOBAL", 2L, second.eventId(), second.chainHash(), 2));

        var bundle = new ExportService(repository, chainHeads, hashService,
                JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC())
                .export("actor-1", null);

        assertEquals("actorId", bundle.filterType());
        assertEquals("actor-1", bundle.filterValue());
        assertEquals(List.of(first, second), bundle.records());
        verify(repository).findAllByActorOrResource("actor-1", null);
    }

    @Test void exportsMatchingRecordsByResourceId() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        AuditEvent event = sampleEvent(1L, "actor-9", "ACCOUNT", "A1");
        when(repository.findAllByActorOrResource(null, "A1")).thenReturn(List.of(event));
        when(chainHeads.findGlobal()).thenReturn(new ChainHead("GLOBAL", 1L, event.eventId(), event.chainHash(), 1));

        var bundle = new ExportService(repository, chainHeads, hashService,
                JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC())
                .export(null, "A1");

        assertEquals("resourceId", bundle.filterType());
        assertEquals("A1", bundle.filterValue());
        assertEquals(List.of(event), bundle.records());
        verify(repository).findAllByActorOrResource(null, "A1");
    }

    @Test void includesWhateverTheRepositoryReturnsIncludingArchivedEvents() {
        // ExportService applies no archive filtering of its own: it exports exactly what
        // AuditEventRepository.findAllByActorOrResource returns. That repository method's own
        // archive-inclusion guarantee (no NOT EXISTS clause, unlike findMatching) is verified
        // separately in AuditEventRepositoryTest.
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        AuditEvent archivedEvent = sampleEvent(1L, "actor-1", "ACCOUNT", "A1");
        when(repository.findAllByActorOrResource("actor-1", null)).thenReturn(List.of(archivedEvent));
        when(chainHeads.findGlobal()).thenReturn(
                new ChainHead("GLOBAL", 1L, archivedEvent.eventId(), archivedEvent.chainHash(), 1));

        var bundle = new ExportService(repository, chainHeads, hashService,
                JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC())
                .export("actor-1", null);

        assertEquals(List.of(archivedEvent), bundle.records());
    }

    @Test void returnsAnEmptyButValidBundleWhenNoRecordsMatch() throws Exception {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        when(repository.findAllByActorOrResource("actor-1", null)).thenReturn(List.of());
        when(chainHeads.findGlobal()).thenReturn(null);

        var bundle = new ExportService(repository, chainHeads, hashService,
                JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC())
                .export("actor-1", null);

        assertTrue(bundle.records().isEmpty());
        assertNotNull(bundle.manifest().bundleDigest());

        var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(bundle.manifest().publicKey())));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(bundle.manifest().bundleDigest().getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(bundle.manifest().signature())));
    }

    @Test void recipientCanIndependentlyRecomputeAndVerifyTheBundleDigest() throws Exception {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        JsonMapper json = JsonMapper.builder().findAndAddModules().build();
        AuditEvent event = sampleEvent(1L, "actor-1", "ACCOUNT", "A1");
        when(repository.findAllByActorOrResource("actor-1", null)).thenReturn(List.of(event));
        when(chainHeads.findGlobal()).thenReturn(new ChainHead("GLOBAL", 1L, event.eventId(), event.chainHash(), 1));

        var bundle = new ExportService(repository, chainHeads, hashService, json, Clock.systemUTC())
                .export("actor-1", null);

        // Recompute the digest exactly as a recipient would, using only the bundle's own content.
        String recomputed = sha256(json.writeValueAsString(bundle.records()) + "|"
                + json.writeValueAsString(bundle.chainAnchor()));
        assertEquals(recomputed, bundle.manifest().bundleDigest());

        var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(bundle.manifest().publicKey())));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(recomputed.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(bundle.manifest().signature())));
    }

    @Test void tamperingWithAnExportedRecordIsDetectable() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ChainHeadRepository chainHeads = mock(ChainHeadRepository.class);
        JsonMapper json = JsonMapper.builder().findAndAddModules().build();
        AuditEvent event = sampleEvent(1L, "actor-1", "ACCOUNT", "A1");
        when(repository.findAllByActorOrResource("actor-1", null)).thenReturn(List.of(event));
        when(chainHeads.findGlobal()).thenReturn(new ChainHead("GLOBAL", 1L, event.eventId(), event.chainHash(), 1));

        var bundle = new ExportService(repository, chainHeads, hashService, json, Clock.systemUTC())
                .export("actor-1", null);

        // A recipient receives a bundle whose record content was altered after it was signed.
        AuditEvent tampered = new AuditEvent(event.sequenceId(), event.eventId(), event.eventType(),
                event.actorId(), event.resourceType(), event.resourceId(),
                Map.of("amount", "9999.00"), event.eventTimestamp(), event.schemaVersion(),
                event.contentHash(), event.previousHash(), event.chainHash());

        String recomputedOverTamperedRecords = sha256(json.writeValueAsString(List.of(tampered)) + "|"
                + json.writeValueAsString(bundle.chainAnchor()));

        assertNotEquals(bundle.manifest().bundleDigest(), recomputedOverTamperedRecords);
    }

    private AuditEvent sampleEvent(long sequenceId, String actorId, String resourceType, String resourceId) {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-09-19T00:00:00Z");
        String contentHash = hashService.sha256("content-" + sequenceId);
        String previousHash = sequenceId <= 1 ? hashService.genesisHash() : hashService.sha256("previous-" + sequenceId);
        String chainHash = hashService.chainHash(previousHash, contentHash);
        return new AuditEvent(sequenceId, eventId, "ACCOUNT_UPDATED", actorId, resourceType, resourceId,
                Map.of("amount", "100.00"), timestamp, 1, contentHash, previousHash, chainHash);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
