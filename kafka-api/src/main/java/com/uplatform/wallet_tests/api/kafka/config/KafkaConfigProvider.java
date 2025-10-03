package com.uplatform.wallet_tests.api.kafka.config;

import com.uplatform.wallet_tests.config.modules.kafka.KafkaConfig;

public interface KafkaConfigProvider {
    KafkaConfig getKafkaConfig();
    String getTopicPrefix();
}
