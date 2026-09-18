package com.audit.log.service;

import com.audit.log.domain.AuditEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

@Service
public class RedactionService {
    private static final String ID = "_redactionId";
    private static final String COMMITMENT = "commitment";
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public RedactionService(JdbcTemplate jdbc, JsonMapper json, Clock clock,
                            @Value("${audit.cryptographic-key}") String configuredKey) {
        this.jdbc = jdbc; this.json = json; this.clock = clock;
        try { this.key = MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public PreparedPayload prepare(UUID eventId, Map<String, Object> original, List<String> paths) {
        Map<String, Object> copy = deepMap(original);
        List<Secret> secrets = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>(paths == null ? List.of() : paths);
        for (String path : unique) {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("Redactable field path must not be blank");
            Map<String, Object> parent = parent(copy, path);
            String leaf = leaf(path);
            if (!parent.containsKey(leaf)) throw new IllegalArgumentException("Redactable field does not exist: " + path);
            Object value = parent.get(leaf);
            UUID redactionId = UUID.randomUUID();
            String serialized = write(value);
            parent.put(leaf, Map.of(ID, redactionId.toString(), COMMITMENT, hmac(serialized)));
            secrets.add(new Secret(redactionId, eventId, path, encrypt(serialized)));
        }
        return new PreparedPayload(copy, secrets);
    }

    public void persist(PreparedPayload prepared) {
        for (Secret secret : prepared.secrets()) {
            jdbc.update("INSERT INTO audit_redaction_value(redaction_id,event_id,field_path,encrypted_value) VALUES (?,?,?,?)",
                    secret.id(), secret.eventId(), secret.path(), secret.ciphertext());
        }
    }

    public AuditEvent hydrate(AuditEvent event) {
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) hydrateValue(deepMap(event.payload()));
        return new AuditEvent(event.sequenceId(), event.eventId(), event.eventType(), event.actorId(),
                event.resourceType(), event.resourceId(), copy, event.eventTimestamp(), event.schemaVersion(),
                event.contentHash(), event.previousHash(), event.chainHash());
    }

    public int redact(UUID eventId, List<String> paths) {
        if (paths == null || paths.isEmpty()) throw new IllegalArgumentException("At least one field path is required");
        int changed = 0;
        for (String path : new LinkedHashSet<>(paths)) {
            changed += jdbc.update("UPDATE audit_redaction_value SET encrypted_value=NULL, redacted_at=? " +
                            "WHERE event_id=? AND field_path=? AND redacted_at IS NULL",
                    Timestamp.from(clock.instant()), eventId, path);
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private Object hydrateValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String,Object> map = (Map<String,Object>) raw;
            if (map.containsKey(ID) && map.containsKey(COMMITMENT)) {
                UUID id = UUID.fromString(String.valueOf(map.get(ID)));
                List<String> values = jdbc.query("SELECT encrypted_value FROM audit_redaction_value WHERE redaction_id=?",
                        (rs, n) -> rs.getString(1), id);
                if (values.isEmpty() || values.get(0) == null) return Map.of("redacted", true);
                return read(decrypt(values.get(0)));
            }
            map.replaceAll((key, nested) -> hydrateValue(nested));
            return map;
        } else if (value instanceof List<?> list) return list.stream().map(this::hydrateValue).toList();
        return value;
    }

    private String hmac(String value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot create redaction commitment", e); }
    }

    private String encrypt(String value) {
        try { byte[] iv = new byte[12]; random.nextBytes(iv); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length]; System.arraycopy(iv,0,out,0,iv.length); System.arraycopy(encrypted,0,out,iv.length,encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) { throw new IllegalStateException("Cannot encrypt redactable value", e); }
    }

    private String decrypt(String encoded) {
        try { byte[] in = Base64.getDecoder().decode(encoded); byte[] iv = Arrays.copyOfRange(in,0,12); byte[] data = Arrays.copyOfRange(in,12,in.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Cannot decrypt redactable value", e); }
    }

    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Unsupported redactable value", e); } }
    private Object read(String value) { try { return json.readValue(value, Object.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> deepMap(Map<String,Object> input) { Map<String,Object> out = new LinkedHashMap<>(); input.forEach((k,v)->out.put(k,deep(v))); return out; }
    private Object deep(Object v) { if(v instanceof Map<?,?> m){ Map<String,Object> out=new LinkedHashMap<>(); m.forEach((k,x)->out.put(String.valueOf(k),deep(x))); return out;} if(v instanceof List<?> l)return l.stream().map(this::deep).toList(); return v; }
    @SuppressWarnings("unchecked") private Map<String,Object> parent(Map<String,Object> root,String path){ String[] p=path.split("\\."); Map<String,Object> at=root; for(int i=0;i<p.length-1;i++){ Object n=at.get(p[i]); if(!(n instanceof Map<?,?>))throw new IllegalArgumentException("Redactable field does not exist: "+path); at=(Map<String,Object>)n;} return at; }
    private String leaf(String path){ String[] p=path.split("\\."); return p[p.length-1]; }

    public record PreparedPayload(Map<String,Object> payload, List<Secret> secrets) { }
    public record Secret(UUID id, UUID eventId, String path, String ciphertext) { }
}
