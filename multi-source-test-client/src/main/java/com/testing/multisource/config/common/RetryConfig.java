package com.testing.multisource.config.common;

/**
 * Common retry configuration used across different modules (databases, services, etc.)
 */
public record RetryConfig(
        int timeoutSeconds,
        long pollIntervalMs,
        long pollDelayMs
) {}
