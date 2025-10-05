package com.testing.multisource.config.modules.kafka;

public record KafkaConsumerConfig(
        String autoOffsetReset,
        boolean enableAutoCommit
) {}
