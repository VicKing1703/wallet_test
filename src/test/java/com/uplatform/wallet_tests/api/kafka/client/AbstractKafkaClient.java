package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.api.kafka.client.KafkaExpectationBuilder;

import java.time.Duration;

public abstract class AbstractKafkaClient {

    protected final KafkaBackgroundConsumer kafkaBackgroundConsumer;
    protected final Duration defaultFindTimeout;

    protected AbstractKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        this.kafkaBackgroundConsumer = kafkaBackgroundConsumer;
        this.defaultFindTimeout = configProvider.getKafkaConfig().getFindMessageTimeout();
    }

    public <T> KafkaExpectationBuilder<T> expect(Class<T> messageClass) {
        return new KafkaExpectationBuilder<>(this.kafkaBackgroundConsumer, this.defaultFindTimeout, messageClass);
    }
}
