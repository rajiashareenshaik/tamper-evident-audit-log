package com.audit.log.persistence;

import java.util.UUID;

/**
 *
 * @param chainId identifier of the chain this head belongs to
 * @param lastSequenceId sequence_id of the most recently appended event, or {@code null} if empty
 * @param lastEventId eventId of the most recently appended event, or {@code null} if empty
 * @param lastChainHash chainHash of the most recently appended event, or the genesis hash if empty
 * @param eventCount total number of events appended to this chain so far
 */
public record ChainHead(
        String chainId,
        Long lastSequenceId,
        UUID lastEventId,
        String lastChainHash,
        long eventCount
) {
}
