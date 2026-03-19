package com.smartfinance.dashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables async execution (used by EmailService) and scheduled tasks.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AppConfig {
}
