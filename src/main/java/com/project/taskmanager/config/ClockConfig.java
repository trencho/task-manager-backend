package com.project.taskmanager.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // The system default zone, which is what LocalDate.now() read before the clock was injected.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
