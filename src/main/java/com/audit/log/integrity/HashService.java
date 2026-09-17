package com.audit.log.integrity;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HashService {

    private static final String GENESIS_INPUT = "AUDIT_CHAIN_GENESIS_V1";

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

    public String genesisHash() {
        return sha256(GENESIS_INPUT);
    }

    public String chainHash(
            String previousHash,
            String contentHash
    ) {
        return sha256(previousHash + contentHash);
    }
}
