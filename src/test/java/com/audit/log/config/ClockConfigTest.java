package com.audit.log.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ClockConfig")
class ClockConfigTest {

    private final ClockConfig clockConfig = new ClockConfig();

    @Test
    @DisplayName("provides a clock in the UTC zone")
    void shouldProvideUtcClock() {

        Clock clock = clockConfig.clock();

        assertEquals(ZoneOffset.UTC, clock.getZone());
    }
}
