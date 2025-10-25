package com.testing.multisource.config.common;

public record RetryConfig(
        int timeoutSeconds,
        long pollIntervalMs,
        long pollDelayMs
) {}
