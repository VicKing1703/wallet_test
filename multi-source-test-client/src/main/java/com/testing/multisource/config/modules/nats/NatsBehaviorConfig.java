package com.testing.multisource.config.modules.nats;

/**
 * NATS behavior configuration
 */
public record NatsBehaviorConfig(
        long searchTimeoutSeconds,
        boolean failOnDeserialization
) {}
