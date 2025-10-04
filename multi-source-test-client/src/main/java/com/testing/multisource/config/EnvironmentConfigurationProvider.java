package com.testing.multisource.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import com.testing.multisource.api.http.config.HttpConfigProvider;
import com.testing.multisource.config.modules.http.HttpModuleProperties;
import com.testing.multisource.api.kafka.config.KafkaConfigProvider;
import com.testing.multisource.api.nats.config.NatsConfigProvider;
import com.testing.multisource.api.redis.config.RedisConfigProvider;
import com.testing.multisource.config.modules.redis.RedisModuleProperties;
import com.testing.multisource.config.modules.redis.RedisAggregateConfig;
import com.testing.multisource.config.modules.kafka.KafkaConfig;
import com.testing.multisource.config.modules.nats.NatsConfig;
import com.testing.multisource.config.modules.kafka.KafkaModuleProperties;
import com.testing.multisource.config.modules.nats.NatsModuleProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

@Service
@Slf4j
@Getter
public class EnvironmentConfigurationProvider implements KafkaConfigProvider, RedisConfigProvider, NatsConfigProvider, HttpConfigProvider {

    private final List<EnvironmentConfigPostProcessor> postProcessors;
    private EnvironmentConfig environmentConfig;
    private ObjectNode rawEnvironmentNode;

    public EnvironmentConfigurationProvider() {
        this(loadPostProcessors());
    }

    EnvironmentConfigurationProvider(List<EnvironmentConfigPostProcessor> postProcessors) {
        this.postProcessors = postProcessors;
    }

    private static List<EnvironmentConfigPostProcessor> loadPostProcessors() {
        ServiceLoader<EnvironmentConfigPostProcessor> loader = ServiceLoader.load(EnvironmentConfigPostProcessor.class);
        List<EnvironmentConfigPostProcessor> result = new ArrayList<>();
        loader.forEach(result::add);
        return result;
    }

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
            ObjectNode rawNode = objectMapper.readTree(configFileStream) instanceof ObjectNode objectNode
                    ? objectNode
                    : throwNonObjectNode(configFileName);
            this.rawEnvironmentNode = rawNode.deepCopy();
            this.environmentConfig = objectMapper.treeToValue(rawNode, EnvironmentConfig.class);
            for (EnvironmentConfigPostProcessor postProcessor : postProcessors) {
                postProcessor.postProcess(this.environmentConfig, rawNode, objectMapper);
            }
        }
        log.info("Successfully loaded configuration for environment '{}'", environmentConfig.getName());
    }

    @Override
    public KafkaConfig getKafkaConfig() {
        KafkaModuleProperties kafka = environmentConfig.getKafka();
        return kafka != null ? kafka.toLegacyKafkaConfig() : null;
    }


    @Override
    public String getTopicPrefix() {
        return environmentConfig.getTopicPrefix();
    }

    @Override
    public NatsConfig getNatsConfig() {
        NatsModuleProperties nats = environmentConfig.getNats();
        return nats != null ? nats.toLegacyNatsConfig() : null;
    }

    @Override
    public String getNatsStreamPrefix() {
        return environmentConfig.getNatsStreamPrefix();
    }

    @Override
    public RedisAggregateConfig getRedisAggregateConfig() {
        RedisModuleProperties redis = environmentConfig.getRedis();
        return redis != null ? redis.getAggregate() : null;
    }

    @Override
    public HttpModuleProperties getHttpConfig() {
        return environmentConfig.getHttp();
    }

    public ObjectNode getRawEnvironmentNode() {
        return rawEnvironmentNode == null ? null : rawEnvironmentNode.deepCopy();
    }

    private static ObjectNode throwNonObjectNode(String configFileName) throws IOException {
        throw new IOException("Configuration file " + configFileName + " must contain a JSON object as the root");
    }
}
