package com.audit.log.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChainHeadRepository")
class ChainHeadRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ChainHeadRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ChainHeadRepository(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("seeds the head row if missing, then locks and maps the current tip")
    void shouldSeedHeadThenLockAndMapExistingRow() throws Exception {

        UUID lastEventId = UUID.randomUUID();

        when(jdbcTemplate.queryForObject(
                anyString(),
                any(RowMapper.class),
                eq(ChainHeadRepository.GLOBAL_CHAIN_ID)
        )).thenAnswer(invocation -> {

            RowMapper<ChainHead> rowMapper = invocation.getArgument(1);

            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("chain_id")).thenReturn("GLOBAL");
            when(resultSet.getObject("last_sequence_id", Long.class)).thenReturn(5L);
            when(resultSet.getObject("last_event_id", UUID.class)).thenReturn(lastEventId);
            when(resultSet.getString("last_chain_hash")).thenReturn("tip-hash");
            when(resultSet.getLong("event_count")).thenReturn(5L);

            return rowMapper.mapRow(resultSet, 0);
        });

        ChainHead chainHead = repository.lockOrCreate("genesis-hash");

        verify(jdbcTemplate).update(
                anyString(),
                eq(ChainHeadRepository.GLOBAL_CHAIN_ID),
                eq("genesis-hash")
        );

        assertEquals("GLOBAL", chainHead.chainId());
        assertEquals(5L, chainHead.lastSequenceId());
        assertEquals(lastEventId, chainHead.lastEventId());
        assertEquals("tip-hash", chainHead.lastChainHash());
        assertEquals(5L, chainHead.eventCount());
    }

    @Test
    @DisplayName("succeeds when the update affects exactly one row")
    void shouldSucceedWhenExactlyOneRowUpdated() {

        when(jdbcTemplate.update(
                anyString(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        repository.update(1L, UUID.randomUUID(), "chain-hash", 1L);
    }

    @Test
    @DisplayName("throws IllegalStateException when the update affects no rows")
    void shouldThrowWhenNoRowsAreUpdated() {

        when(jdbcTemplate.update(
                anyString(), any(), any(), any(), any(), any()
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> repository.update(1L, UUID.randomUUID(), "chain-hash", 1L)
        );
    }
}
