package com.rhitam.jobtracker.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Injected everywhere instead of Instant.now() so time is controllable in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
