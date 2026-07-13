package com.turing.vigilant.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Cross-cutting infrastructure beans. */
@Configuration
public class SupportConfiguration {

    /** A single UTC clock so services can be tested with a fixed instant. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
