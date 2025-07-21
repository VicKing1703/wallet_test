package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.api.kafka.dto.PaymentTransactionMessage;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentKafkaClient extends AbstractKafkaClient {

    public PaymentKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        super(kafkaBackgroundConsumer, configProvider);
    }

    public PaymentTransactionMessage expectTransactionMessage(String playerId, String nodeId) {
        Map<String, String> filter = Map.of(
                "playerId", playerId,
                "nodeId", nodeId
        );
        return expectMessage(filter, PaymentTransactionMessage.class);
    }

    public PaymentTransactionMessage expectUniqueTransactionMessage(String playerId, String nodeId) {
        Map<String, String> filter = Map.of(
                "playerId", playerId,
                "nodeId", nodeId
        );
        return expectUniqueMessage(filter, PaymentTransactionMessage.class);
    }
}
