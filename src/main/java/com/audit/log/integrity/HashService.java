package com.audit.log.integrity;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 primitives used to build and verify the global audit hash chain.
 */
@Component
public class HashService {

    private static final String GENESIS_INPUT = "AUDIT_CHAIN_GENESIS_V1";

    /**
     * @param value the input to hash, treated as UTF-8
     * @return the lowercase hex-encoded SHA-256 digest of {@code value}
     */
    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    e
            );
        }
    }

    /**
     * @return the fixed hash used as {@code previousHash} for the very first event in the chain
     */
    public String genesisHash() {
        return sha256(GENESIS_INPUT);
    }

    /**
     * Links an event into the chain by binding its content to the hash that came before it.
     *
     * @param previousHash the chainHash of the preceding event (or {@link #genesisHash()} if this
     *                      is the first event)
     * @param contentHash the SHA-256 of this event's own canonicalized content
     * @return the chainHash for this event
     */
    public String chainHash(
            String previousHash,
            String contentHash
    ) {
        return sha256(previousHash + contentHash);
    }
}
