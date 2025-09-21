package com.uplatform.wallet_tests.api.redis.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uplatform.wallet_tests.api.redis.RedisDataType;
import com.uplatform.wallet_tests.api.redis.RedisTypeMappingRegistry;
import com.uplatform.wallet_tests.api.redis.model.WalletData;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import com.uplatform.wallet_tests.config.RedisAggregateConfig;
import com.uplatform.wallet_tests.api.redis.config.RedisConfigProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Configuration
public class RedisConfig {

    @Bean
    @ConfigurationProperties(prefix = "redis")
    public RedisModuleProperties redisModuleProperties() {
        return new RedisModuleProperties();
    }

    @Bean
    public RedisTypeMappingRegistry redisTypeMappingRegistry() {
        return new RedisTypeMappingRegistry()
                .register(RedisDataType.WALLET_AGGREGATE, new TypeReference<WalletFullData>() {})
                .register(RedisDataType.PLAYER_WALLETS, new TypeReference<Map<String, WalletData>>() {});
    }

    @Bean
    public RedisAwaitilityProperties redisAwaitilityProperties(RedisConfigProvider configProvider) {
        RedisAggregateConfig aggregateConfig = Optional.ofNullable(configProvider.getRedisAggregateConfig())
                .orElseThrow(() -> new IllegalStateException("Redis aggregate configuration is missing"));

        Duration pollInterval = Duration.ofMillis(aggregateConfig.retryDelayMs());
        int attempts = Math.max(aggregateConfig.retryAttempts(), 1);
        Duration timeout = pollInterval.multipliedBy(attempts);
        return new RedisAwaitilityProperties(timeout, pollInterval);
    }

    @Bean
    public static RedisClientBeanDefinitionRegistrar redisClientBeanDefinitionRegistrar() {
        return new RedisClientBeanDefinitionRegistrar();
    }
}
