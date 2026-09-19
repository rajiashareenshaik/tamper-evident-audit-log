package com.audit.log.api;

import com.audit.log.domain.AuditEvent;
import java.time.Instant;
import java.util.List;

public record ExportBundle(
        String formatVersion,
        Instant exportedAt,
        String filterType,
        String filterValue,
        List<AuditEvent> records,
        Manifest manifest
) {
    public record Manifest(String digestAlgorithm, String recordsDigest, String signatureAlgorithm,
                           String signature, String publicKey) { }
}
