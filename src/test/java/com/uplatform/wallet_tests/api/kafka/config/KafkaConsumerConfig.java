package com.uplatform.wallet_tests.api.kafka.config;

import com.testing.multisource.api.kafka.config.KafkaTopicMappingRegistry;
import com.testing.multisource.api.kafka.config.SimpleKafkaTopicMappingRegistry;
import com.uplatform.wallet_tests.api.kafka.dto.GameCategoryMessage;
import com.uplatform.wallet_tests.api.kafka.dto.GameSessionStartMessage;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.kafka.dto.PlayerAccountMessage;
import com.uplatform.wallet_tests.api.kafka.dto.player_status.PlayerStatusUpdateMessage;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.kafka.dto.PaymentTransactionMessage;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.BrandEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameBrandEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameCategoryEvent;
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
        mappings.put(PlayerStatusUpdateMessage.class, "player.v1.account");
        mappings.put(WalletProjectionMessage.class, "wallet.v8.projectionSource");
        mappings.put(GameSessionStartMessage.class, "core.gambling.v1.GameSessionStart");
        mappings.put(GameCategoryMessage.class, "core.gambling.v3.Game");
        mappings.put(LimitMessage.class, "limits.v2");
        mappings.put(PaymentTransactionMessage.class, "payment.v1.transaction");
        mappings.put(BrandEvent.class, "core.gambling.v1.Brand");
        mappings.put(GameBrandEvent.class, "core.gambling.v3.Game");
        mappings.put(GameCategoryEvent.class, "core.gambling.v3.Game");

        return new SimpleKafkaTopicMappingRegistry(mappings);
    }
}