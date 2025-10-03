package com.uplatform.wallet_tests.api.kafka.config;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.uplatform.wallet_tests.api.kafka.dto.GameSessionStartMessage;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.kafka.dto.PlayerAccountMessage;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.kafka.dto.PaymentTransactionMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public KafkaTopicMappingRegistry kafkaTopicMappingRegistry() {
        Map<Class<?>, String> mappings = new HashMap<>();

        mappings.put(PlayerAccountMessage.class, "player.v1.account");
        mappings.put(WalletProjectionMessage.class, "wallet.v8.projectionSource");
        mappings.put(GameSessionStartMessage.class, "core.gambling.v1.GameSessionStart");
        mappings.put(LimitMessage.class, "limits.v2");
        mappings.put(PaymentTransactionMessage.class, "payment.v1.transaction");

        return new SimpleKafkaTopicMappingRegistry(mappings);
    }
}