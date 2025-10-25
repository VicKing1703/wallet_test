package com.testing.multisource.config.modules.nats;

public record NatsStreamConfig(
        String streamName,
        long uniqueDuplicateWindowMs
) {}
