package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.config.KafkaConfigProvider;
import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class KafkaClient {

    private final KafkaBackgroundConsumer kafkaBackgroundConsumer;
    private final Duration defaultFindTimeout;

    public KafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            KafkaConfigProvider configProvider
    ) {
        this.kafkaBackgroundConsumer = kafkaBackgroundConsumer;
        this.defaultFindTimeout = configProvider.getKafkaConfig().findMessageTimeout();
    }

    public <T> KafkaExpectationBuilder<T> expect(Class<T> messageClass) {
        return new KafkaExpectationBuilder<>(this.kafkaBackgroundConsumer, this.defaultFindTimeout, messageClass);
    }
}
