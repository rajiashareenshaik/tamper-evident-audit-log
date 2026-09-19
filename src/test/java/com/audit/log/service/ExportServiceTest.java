package com.audit.log.service;

import com.audit.log.persistence.AuditEventRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportServiceTest {
    @Test void createsAnIndependentlyVerifiableSignedManifest() throws Exception {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.findAllByActorOrResource("actor-1", null)).thenReturn(List.of());
        var bundle = new ExportService(repository, JsonMapper.builder().build(), Clock.systemUTC())
                .export("actor-1", null);
        var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(bundle.manifest().publicKey())));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(bundle.manifest().recordsDigest().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(bundle.manifest().signature())));
    }

    @Test void requiresExactlyOneFilter() {
        var service = new ExportService(mock(AuditEventRepository.class), JsonMapper.builder().build(), Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> service.export(null, null));
        assertThrows(IllegalArgumentException.class, () -> service.export("a", "r"));
    }
}
