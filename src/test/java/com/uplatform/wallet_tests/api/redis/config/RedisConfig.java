package com.uplatform.wallet_tests.api.redis.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uplatform.wallet_tests.api.redis.RedisDataType;
import com.uplatform.wallet_tests.api.redis.RedisTypeMappingRegistry;
import com.uplatform.wallet_tests.api.redis.model.WalletData;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTypeMappingRegistry redisTypeMappingRegistry() {
        return new RedisTypeMappingRegistry()
                .register(RedisDataType.WALLET_AGGREGATE, new TypeReference<WalletFullData>() {})
                .register(RedisDataType.PLAYER_WALLETS, new TypeReference<Map<String, WalletData>>() {});
    }
}

