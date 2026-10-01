package com.nbfc.itsm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (e.g. closing resolved tickets the requester did not confirm). Off in tests: itsm.jobs.enabled=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "itsm.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
