package com.testing.multisource.config.modules.nats;

/**
 * NATS stream configuration
 */
public record NatsStreamConfig(
        String streamName,
        long uniqueDuplicateWindowMs
) {}
