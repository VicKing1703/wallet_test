package com.testing.multisource.api.kafka.config;

import com.testing.multisource.config.modules.kafka.KafkaConfig;

public interface KafkaConfigProvider {
    KafkaConfig getKafkaConfig();
    String getTopicPrefix();
}
