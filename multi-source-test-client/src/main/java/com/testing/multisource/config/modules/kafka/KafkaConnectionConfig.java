package com.testing.multisource.config.modules.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KafkaConnectionConfig(
        @JsonProperty("bootstrapServer") String bootstrapServers,
        String groupId
) {}
