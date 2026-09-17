package com.audit.log.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the single {@link Clock} bean the service uses for all server-assigned timestamps,
 * so time-dependent code can be tested against a fixed or offset clock instead of the system one.
 */
@Configuration
public class ClockConfig {

    /**
     * @return a UTC system clock, used as the source of truth for event timestamps
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
