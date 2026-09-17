package com.audit.log.integrity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashServiceTest {

    private final HashService hashService =
            new HashService();

    @Test
    void shouldProduceStableSha256Hash() {

        String first =
                hashService.sha256("audit-event");

        String second =
                hashService.sha256("audit-event");

        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void shouldProduceDifferentHashForDifferentInput() {

        String first =
                hashService.sha256("event-one");

        String second =
                hashService.sha256("event-two");

        assertNotEquals(first, second);
    }

    @Test
    void shouldProduceStableGenesisHash() {

        String first =
                hashService.genesisHash();

        String second =
                hashService.genesisHash();

        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void shouldIncludePreviousHashInChainHash() {

        String contentHash =
                hashService.sha256("content");

        String first =
                hashService.chainHash(
                        hashService.sha256("previous-one"),
                        contentHash
                );

        String second =
                hashService.chainHash(
                        hashService.sha256("previous-two"),
                        contentHash
                );

        assertNotEquals(first, second);
    }
}
