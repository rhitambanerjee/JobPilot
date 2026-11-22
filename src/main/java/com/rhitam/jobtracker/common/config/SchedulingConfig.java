package com.rhitam.jobtracker.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Can be switched off (tracker.scheduling.enabled=false) so integration tests drive pollers by hand. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "tracker.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
