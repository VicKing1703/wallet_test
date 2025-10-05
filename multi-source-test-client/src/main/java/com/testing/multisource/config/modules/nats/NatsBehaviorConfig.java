package com.testing.multisource.config.modules.nats;

public record NatsBehaviorConfig(
        long searchTimeoutSeconds,
        boolean failOnDeserialization
) {}
