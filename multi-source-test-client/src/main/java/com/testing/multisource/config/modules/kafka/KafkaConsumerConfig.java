package com.testing.multisource.config.modules.kafka;

/**
 * Kafka consumer-specific configuration
 */
public record KafkaConsumerConfig(
        String autoOffsetReset,
        boolean enableAutoCommit
) {}
