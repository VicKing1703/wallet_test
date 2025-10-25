package com.testing.multisource.api.redis.config;

import com.testing.multisource.config.modules.redis.RedisAggregateConfig;
import com.testing.multisource.config.modules.redis.RedisModuleProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Optional;

@Configuration
@Conditional(RedisClientsConfiguredCondition.class)
public class RedisApiAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "redis")
    public RedisModuleProperties redisModuleProperties() {
        return new RedisModuleProperties();
    }

    @Bean
    public RedisAwaitilityProperties redisAwaitilityProperties(RedisModuleProperties redisModuleProperties) {
        RedisAggregateConfig aggregateConfig = Optional.ofNullable(redisModuleProperties.getAggregate())
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
