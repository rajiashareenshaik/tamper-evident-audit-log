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
        ChainAnchor chainAnchor,
        Manifest manifest
) {
    /**
     * A checkpoint of the global chain at export time, so a recipient can tell how far this
     * export sits relative to the whole chain. It is not a full inclusion proof: a recipient can
     * confirm the first exported record genuinely opens the chain only if its own
     * {@code previousHash} equals {@code genesisHash}; otherwise there is no way, from the bundle
     * alone, to prove which earlier (unexported) event it really follows.
     */
    public record ChainAnchor(
            String genesisHash,
            String chainHeadHash,
            long chainEventCount,
            Long chainLastSequenceId
    ) { }

    public record Manifest(String digestAlgorithm, String bundleDigest, String signatureAlgorithm,
                           String signature, String publicKey) { }
}
