package com.testing.multisource.config.modules.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.testing.multisource.config.modules.kafka.KafkaConfig;

import java.time.Duration;

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

    public KafkaConnectionConfig connection() {
        return new KafkaConnectionConfig(bootstrapServers, groupId);
    }

    public KafkaClientConfig client() {
        return new KafkaClientConfig(
                bufferSize,
                findMessageTimeout,
                findMessageSleepInterval,
                pollDuration,
                shutdownTimeout
        );
    }

    public KafkaConsumerConfig consumer() {
        return new KafkaConsumerConfig(autoOffsetReset, enableAutoCommit);
    }
}
