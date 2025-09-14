package com.uplatform.wallet_tests.api.kafka.config;

import com.uplatform.wallet_tests.config.KafkaConfig;

public interface KafkaConfigProvider {
    KafkaConfig getKafkaConfig();
    String getTopicPrefix();
}
