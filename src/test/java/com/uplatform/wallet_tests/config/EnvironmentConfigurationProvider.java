package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import com.uplatform.wallet_tests.api.kafka.config.KafkaConfigProvider;
import com.uplatform.wallet_tests.api.redis.config.RedisConfigProvider;
import com.uplatform.wallet_tests.api.redis.config.RedisModuleProperties;
import com.uplatform.wallet_tests.config.RedisAggregateConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

@Service
@Slf4j
@Getter
public class EnvironmentConfigurationProvider implements KafkaConfigProvider, RedisConfigProvider {

    private EnvironmentConfig environmentConfig;

    @PostConstruct
    public void loadConfig() throws IOException {
        String envName = System.getProperty("env");
        if (envName == null || envName.trim().isEmpty()) {
            throw new IllegalStateException(
                    "System property 'env' is not set! Please run tests with -Denv=<environment_name>"
            );
        }

        log.info("Loading configuration for environment: {}", envName);
        String configFileName = "configs/" + envName + ".json";

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        SimpleModule durationModule = new SimpleModule();
        durationModule.addDeserializer(Duration.class, new SpringDurationDeserializer());
        objectMapper.registerModule(durationModule);
        try (InputStream configFileStream = getClass().getClassLoader().getResourceAsStream(configFileName)) {
            if (configFileStream == null) {
                throw new IOException("Configuration file not found in classpath: " + configFileName);
            }
            this.environmentConfig = objectMapper.readValue(configFileStream, EnvironmentConfig.class);
        }
        log.info("Successfully loaded configuration for environment '{}'", environmentConfig.getName());
    }

    @Override
    public KafkaConfig getKafkaConfig() {
        return environmentConfig.getKafka();
    }


    @Override
    public String getTopicPrefix() {
        return environmentConfig.getTopicPrefix();
    }

    public NatsConfig getNatsConfig() {
        return environmentConfig.getNats();
    }

    @Override
    public RedisAggregateConfig getRedisAggregateConfig() {
        RedisModuleProperties redis = environmentConfig.getRedis();
        return redis != null ? redis.getAggregate() : null;
    }
}
