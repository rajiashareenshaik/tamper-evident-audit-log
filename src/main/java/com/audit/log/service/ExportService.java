package com.audit.log.service;

import com.audit.log.api.ExportBundle;
import com.audit.log.domain.AuditEvent;
import com.audit.log.persistence.AuditEventRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

@Service
public class ExportService {
    private final AuditEventRepository repository;
    private final JsonMapper json;
    private final Clock clock;
    private final KeyPair signingKey;

    public ExportService(AuditEventRepository repository, JsonMapper json, Clock clock) {
        this.repository = repository; this.json = json; this.clock = clock;
        try { KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519"); this.signingKey = generator.generateKeyPair(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("Ed25519 is unavailable", e); }
    }

    public ExportBundle export(String actorId, String resourceId) {
        if ((actorId == null) == (resourceId == null))
            throw new IllegalArgumentException("Exactly one of actorId or resourceId is required");
        List<AuditEvent> records = repository.findAllByActorOrResource(actorId, resourceId);
        String digest = sha256(write(records));
        return new ExportBundle("audit-export-v1", clock.instant(), actorId != null ? "actorId" : "resourceId",
                actorId != null ? actorId : resourceId, records,
                new ExportBundle.Manifest("SHA-256", digest, "Ed25519", sign(digest),
                        Base64.getEncoder().encodeToString(signingKey.getPublic().getEncoded())));
    }

    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Cannot serialize export", e); } }
    private String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String sign(String digest) { try { Signature signature = Signature.getInstance("Ed25519"); signature.initSign(signingKey.getPrivate()); signature.update(digest.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(signature.sign()); } catch (Exception e) { throw new IllegalStateException("Cannot sign export", e); } }
}
