package com.uplatform.wallet_tests.config;

import com.uplatform.wallet_tests.api.redis.config.RedisInstanceProperties;
import com.uplatform.wallet_tests.api.redis.config.RedisModuleProperties;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DynamicPropertiesConfigurator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final EnvironmentConfigurationProvider provider = new EnvironmentConfigurationProvider();
    static {
        try {
            provider.loadConfig();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load environment configuration during class initialization", e);
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        EnvironmentConfig config = provider.getEnvironmentConfig();

        List<String> properties = new ArrayList<>();

        if (config.getApi() != null) {
            properties.add("app.api.fapi.base-url=" + config.getApi().getBaseUrl());
            properties.add("app.api.cap.base-url=https://cap." + config.getApi().getBaseUrl().replace("https://", ""));
            properties.add("app.api.manager.base-url=" + config.getApi().getBaseUrl());
            if (config.getApi().getCapCredentials() != null) {
                properties.add("app.api.cap.credentials.username=" + config.getApi().getCapCredentials().getUsername());
                properties.add("app.api.cap.credentials.password=" + config.getApi().getCapCredentials().getPassword());
            }
            if (config.getApi().getManager() != null) {
                properties.add("app.api.manager.secret=" + config.getApi().getManager().getSecret());
                properties.add("app.api.manager.casino-id=" + config.getApi().getManager().getCasinoId());
            }
            if (config.getApi().getConcurrency() != null) {
                properties.add("app.api.concurrency.request-timeout-ms=" + config.getApi().getConcurrency().getRequestTimeoutMs());
                properties.add("app.api.concurrency.default-request-count=" + config.getApi().getConcurrency().getDefaultRequestCount());
            }
        }

        if (config.getPlatform() != null) {
            properties.add("app.settings.default.platform-node-id=" + config.getPlatform().getNodeId());
            properties.add("app.settings.default.platform-group-id=" + config.getPlatform().getGroupId());
            properties.add("app.settings.default.currency=" + config.getPlatform().getCurrency());
            properties.add("app.settings.default.country=" + config.getPlatform().getCountry());
        }

        if (config.getDatabases() != null) {
            config.getDatabases().forEach((name, dbConfig) -> {
                String dbNameForUrl = config.getName() + "_" + name;
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                        dbConfig.getHost(), dbConfig.getPort(), dbNameForUrl);

                properties.add("spring.datasource." + name + ".url=" + url);
                properties.add("spring.datasource." + name + ".username=" + dbConfig.getUsername());
                properties.add("spring.datasource." + name + ".password=" + dbConfig.getPassword());
                properties.add("app.db.retry-timeout-seconds=" + dbConfig.getRetryTimeoutSeconds());
                properties.add("app.db.retry-poll-interval-ms=" + dbConfig.getRetryPollIntervalMs());
                properties.add("app.db.retry-poll-delay-ms=" + dbConfig.getRetryPollDelayMs());
            });
        }

        RedisModuleProperties redisProperties = config.getRedis();

        if (redisProperties != null) {
            RedisAggregateConfig aggregateConfig = redisProperties.getAggregate();
            if (aggregateConfig != null) {
                properties.add("app.redis.aggregate.max-gambling.count=" + aggregateConfig.maxGamblingCount());
                properties.add("app.redis.aggregate.max-iframe.count=" + aggregateConfig.maxIframeCount());
                properties.add("app.redis.retry-attempts=" + aggregateConfig.retryAttempts());
                properties.add("app.redis.retry-delay-ms=" + aggregateConfig.retryDelayMs());
            }

            if (redisProperties.getInstances() != null) {
                redisProperties.getInstances().forEach((instanceName, instanceConfig) -> {
                    if (instanceConfig.getHost() != null) {
                        properties.add("redis.instances." + instanceName + ".host=" + instanceConfig.getHost());
                    }
                    properties.add("redis.instances." + instanceName + ".port=" + instanceConfig.getPort());
                    properties.add("redis.instances." + instanceName + ".database=" + instanceConfig.getDatabase());

                    if (instanceConfig.getPassword() != null) {
                        properties.add("redis.instances." + instanceName + ".password=" + instanceConfig.getPassword());
                    }

                    Duration timeout = instanceConfig.getTimeout();
                    if (timeout != null) {
                        properties.add("redis.instances." + instanceName + ".timeout=" + formatDuration(timeout));
                    }

                    RedisInstanceProperties.LettucePoolProperties poolConfig = instanceConfig.getLettucePool();
                    if (poolConfig != null) {
                        if (poolConfig.getMaxActive() != null) {
                            properties.add("redis.instances." + instanceName + ".lettuce-pool.max-active=" + poolConfig.getMaxActive());
                        }
                        if (poolConfig.getMaxIdle() != null) {
                            properties.add("redis.instances." + instanceName + ".lettuce-pool.max-idle=" + poolConfig.getMaxIdle());
                        }
                        if (poolConfig.getMinIdle() != null) {
                            properties.add("redis.instances." + instanceName + ".lettuce-pool.min-idle=" + poolConfig.getMinIdle());
                        }
                        Duration maxWait = poolConfig.getMaxWait();
                        if (maxWait != null) {
                            properties.add("redis.instances." + instanceName + ".lettuce-pool.max-wait=" + formatDuration(maxWait));
                        }
                        Duration shutdownTimeout = poolConfig.getShutdownTimeout();
                        if (shutdownTimeout != null) {
                            properties.add("redis.instances." + instanceName + ".lettuce-pool.shutdown-timeout=" + formatDuration(shutdownTimeout));
                        }
                    }
                });
            }

            if (redisProperties.getClients() != null) {
                redisProperties.getClients().forEach((clientName, clientConfig) -> {
                    if (clientConfig.getInstanceRef() != null) {
                        properties.add("redis.clients." + clientName + ".instance-ref=" + clientConfig.getInstanceRef());
                    }
                    if (clientConfig.getDataType() != null) {
                        properties.add("redis.clients." + clientName + ".data-type=" + clientConfig.getDataType());
                    }
                });
            }
        }

        if (!properties.isEmpty()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, properties.toArray(new String[0]));
        }
    }

    private static String formatDuration(Duration duration) {
        return duration.toMillis() + "ms";
    }
}
