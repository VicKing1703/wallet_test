package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.api.kafka.config.KafkaConfigProvider;
import org.springframework.stereotype.Component;

@Component
public class KafkaClient extends AbstractKafkaClient {
    public KafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            KafkaConfigProvider configProvider
    ) {
        super(kafkaBackgroundConsumer, configProvider);
    }
}
