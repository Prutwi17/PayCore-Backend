package com.paycore.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled background processing for outbox publishing and webhook retries.
 * Disabled in the test profile to prevent background scheduler threads from blocking test JVM termination.
 */
@Configuration
@EnableScheduling
@Profile("!test")
@ConditionalOnProperty(name = "paycore.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {
}
