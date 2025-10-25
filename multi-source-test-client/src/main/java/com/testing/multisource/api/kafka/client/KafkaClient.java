package com.testing.multisource.api.kafka.client;

import com.testing.multisource.api.kafka.config.KafkaConfigProvider;
import com.testing.multisource.api.kafka.consumer.KafkaBackgroundConsumer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class KafkaClient {

    private final KafkaBackgroundConsumer kafkaBackgroundConsumer;
    private final Duration defaultFindTimeout;
    private final Duration defaultUniqueWindow;

    public KafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            KafkaConfigProvider configProvider
    ) {
        this.kafkaBackgroundConsumer = kafkaBackgroundConsumer;
        this.defaultFindTimeout = configProvider.getKafkaConfig().findMessageTimeout();
        this.defaultUniqueWindow = Duration.ofMillis(configProvider.getKafkaConfig().uniqueDuplicateWindowMs());
    }

    public <T> KafkaExpectationBuilder<T> expect(Class<T> messageClass) {
        return new KafkaExpectationBuilder<>(this.kafkaBackgroundConsumer, this.defaultFindTimeout, this.defaultUniqueWindow, messageClass);
    }

    public Duration getDefaultUniqueWindow() {
        return defaultUniqueWindow;
    }
}
