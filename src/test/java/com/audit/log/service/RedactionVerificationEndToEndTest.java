package com.audit.log.service;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the cross-feature guarantee at the heart of structured redaction: {@code audit_event}
 * (and therefore chain verification) is completely unaffected by redaction, because redaction
 * only ever touches the separate {@code audit_redaction_value} table.
 *
 * <p>Uses a tiny in-memory fake for the one table {@link RedactionService} talks to via
 * {@link JdbcTemplate}, so the test exercises the real {@link RedactionService} and
 * {@link ChainVerificationService} logic without a live database.
 */
@DisplayName("Redaction + chain verification")
class RedactionVerificationEndToEndTest {

    private record RedactionRow(UUID redactionId, UUID eventId, String path, String encryptedValue) {
    }

    private final List<RedactionRow> table = new ArrayList<>();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC);
    private final RedactionService redactionService =
            new RedactionService(jdbc, JsonMapper.builder().findAndAddModules().build(), clock, "end-to-end-test-key");

    private final CanonicalJsonService canonicalJsonService =
            new CanonicalJsonService(JsonMapper.builder().findAndAddModules().build());
    private final HashService hashService = new HashService();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void fakeTheRedactionValueTable() {

        when(jdbc.update(startsWith("INSERT INTO audit_redaction_value"), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    table.add(new RedactionRow(
                            invocation.getArgument(1), invocation.getArgument(2),
                            invocation.getArgument(3), invocation.getArgument(4)));
                    return 1;
                });

        when(jdbc.update(startsWith("UPDATE audit_redaction_value"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UUID eventId = invocation.getArgument(2);
                    String path = invocation.getArgument(3);
                    int changed = 0;
                    for (int i = 0; i < table.size(); i++) {
                        RedactionRow row = table.get(i);
                        if (row.eventId().equals(eventId) && row.path().equals(path) && row.encryptedValue() != null) {
                            table.set(i, new RedactionRow(row.redactionId(), row.eventId(), row.path(), null));
                            changed++;
                        }
                    }
                    return changed;
                });

        when(jdbc.query(startsWith("SELECT encrypted_value"), any(RowMapper.class), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID redactionId = invocation.getArgument(2);
                    return table.stream()
                            .filter(row -> row.redactionId().equals(redactionId))
                            .map(RedactionRow::encryptedValue)
                            .toList();
                });
    }

    @Test
    @DisplayName("verification is intact both before and after redaction, and repeated redaction is idempotent")
    void verificationSurvivesRedaction() {

        UUID eventId = UUID.randomUUID();
        Instant eventTimestamp = Instant.parse("2026-09-19T00:00:00Z");

        var prepared = redactionService.prepare(
                eventId,
                Map.of("profile", Map.of("ssn", "123-45-6789", "name", "Ada")),
                List.of("profile.ssn"));
        redactionService.persist(prepared);

        // Mirrors exactly what AuditCommandService.create() computes and stores: the payload with
        // the commitment placeholder swapped in, hashed once, and never touched again.
        CanonicalAuditContent content = new CanonicalAuditContent(
                eventId, "ACCOUNT_UPDATED", "user-17", "ACCOUNT", "account-42",
                prepared.payload(), eventTimestamp, 1);
        String contentHash = hashService.sha256(canonicalJsonService.canonicalize(content));
        String previousHash = hashService.genesisHash();
        String chainHash = hashService.chainHash(previousHash, contentHash);

        AuditEvent storedEvent = new AuditEvent(1L, eventId, "ACCOUNT_UPDATED", "user-17",
                "ACCOUNT", "account-42", prepared.payload(), eventTimestamp, 1,
                contentHash, previousHash, chainHash);

        AuditEventRepository events = mock(AuditEventRepository.class);
        ChainHeadRepository heads = mock(ChainHeadRepository.class);
        when(events.findAllForVerification()).thenReturn(List.of(storedEvent));
        when(heads.findGlobal()).thenReturn(new ChainHead("GLOBAL", 1L, eventId, chainHash, 1));

        ChainVerificationService verification =
                new ChainVerificationService(events, heads, canonicalJsonService, hashService);

        assertTrue(verification.verify().intact(), "chain must be intact before redaction");
        assertEquals("123-45-6789", ssnFrom(redactionService.hydrate(storedEvent)));

        assertEquals(1, redactionService.redact(eventId, List.of("profile.ssn")));

        // audit_event was never touched by redact(), so re-verifying the SAME stored row must
        // still succeed, with byte-identical hashes to before.
        var afterRedaction = verification.verify();
        assertTrue(afterRedaction.intact(), "chain must remain intact after redaction");
        assertEquals(Map.of("redacted", true), ssnMapFrom(redactionService.hydrate(storedEvent)));

        // Repeated redaction is idempotent: the second call changes nothing.
        assertEquals(0, redactionService.redact(eventId, List.of("profile.ssn")));

        // Redacting a path that was never prepared is a safe no-op, not an error.
        assertEquals(0, redactionService.redact(eventId, List.of("profile.unknownField")));
    }

    @SuppressWarnings("unchecked")
    private String ssnFrom(AuditEvent event) {
        Map<String, Object> profile = (Map<String, Object>) event.payload().get("profile");
        return (String) profile.get("ssn");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ssnMapFrom(AuditEvent event) {
        Map<String, Object> profile = (Map<String, Object>) event.payload().get("profile");
        return (Map<String, Object>) profile.get("ssn");
    }
}
