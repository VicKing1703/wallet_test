package com.testing.multisource.config.modules.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.testing.multisource.config.modules.kafka.KafkaConfig;

import java.time.Duration;

/**
 * Kafka module configuration with structured sub-configs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KafkaModuleProperties(
        @JsonProperty("bootstrapServer") String bootstrapServers,
        String groupId,
        int bufferSize,
        Duration findMessageTimeout,
        Duration findMessageSleepInterval,
        Duration pollDuration,
        Duration shutdownTimeout,
        String autoOffsetReset,
        boolean enableAutoCommit
) {
    /**
     * Legacy compatibility: convert to old KafkaConfig
     */
    public KafkaConfig toLegacyKafkaConfig() {
        return new KafkaConfig(
                bootstrapServers,
                groupId,
                bufferSize,
                findMessageTimeout,
                findMessageSleepInterval,
                pollDuration,
                shutdownTimeout,
                autoOffsetReset,
                enableAutoCommit
        );
    }

    /**
     * Get connection configuration
     */
    public KafkaConnectionConfig connection() {
        return new KafkaConnectionConfig(bootstrapServers, groupId);
    }

    /**
     * Get client configuration
     */
    public KafkaClientConfig client() {
        return new KafkaClientConfig(
                bufferSize,
                findMessageTimeout,
                findMessageSleepInterval,
                pollDuration,
                shutdownTimeout
        );
    }

    /**
     * Get consumer configuration
     */
    public KafkaConsumerConfig consumer() {
        return new KafkaConsumerConfig(autoOffsetReset, enableAutoCommit);
    }
}
