package com.audit.log.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Persistence for the global chain's current tip. Holds no transaction annotations itself: the
 * row lock taken by {@link #lockOrCreate} is only meaningful, and only released, inside a
 * transaction managed by the caller.
 */
@Repository
public class ChainHeadRepository {

    public static final String GLOBAL_CHAIN_ID = "GLOBAL";

    private final JdbcTemplate jdbcTemplate;

    public ChainHeadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Seeds the global chain head row if it doesn't exist yet, then reads and row-locks
     * ({@code SELECT ... FOR UPDATE}) the current tip so concurrent writers serialize on it.
     * The lock is held until the caller's surrounding transaction commits or rolls back.
     *
     * @param genesisHash hash to seed {@code last_chain_hash} with if this chain is brand new
     * @return the current, now-locked chain head
     */

    /**
     * Advances the global chain head to reflect the event most recently appended.
     *
     * @param sequenceId sequence_id of the newly appended event
     * @param eventId eventId of the newly appended event
     * @param chainHash chainHash of the newly appended event, becoming the new tip
     * @param eventCount new total event count for the chain
     * @throws IllegalStateException if the update doesn't affect exactly one row
     */
    public void update(
            long sequenceId,
            UUID eventId,
            String chainHash,
            long eventCount
    ) {

        int rowsUpdated = jdbcTemplate.update(
                """
                UPDATE audit_chain_head
                SET
                    last_sequence_id = ?,
                    last_event_id = ?,
                    last_chain_hash = ?,
                    event_count = ?
                WHERE chain_id = ?
                """,
                sequenceId,
                eventId,
                chainHash,
                eventCount,
                GLOBAL_CHAIN_ID
        );

        if (rowsUpdated != 1) {
            throw new IllegalStateException(
                    "Expected to update exactly one audit chain head row"
            );
        }
    }
}