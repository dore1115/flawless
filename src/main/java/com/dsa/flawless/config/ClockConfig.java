package com.dsa.flawless.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {
    @Bean
    public Clock clock(@Value("${app.timezone}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
