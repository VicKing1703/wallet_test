package com.uplatform.wallet_tests.config;

import com.uplatform.wallet_tests.api.http.config.HttpConcurrencyProperties;
import com.uplatform.wallet_tests.api.http.config.HttpDefaultsProperties;
import com.uplatform.wallet_tests.api.http.config.HttpModuleProperties;
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

        HttpModuleProperties httpConfig = config.getHttp();
        if (httpConfig != null) {
            HttpDefaultsProperties defaults = httpConfig.getDefaults();
            HttpConcurrencyProperties concurrency = defaults != null ? defaults.getConcurrency() : null;

            if (concurrency != null) {
                if (concurrency.getRequestTimeoutMs() != null) {
                    properties.add("app.http.defaults.concurrency.request-timeout-ms=" + concurrency.getRequestTimeoutMs());
                    properties.add("app.api.concurrency.request-timeout-ms=" + concurrency.getRequestTimeoutMs());
                }
                if (concurrency.getDefaultRequestCount() != null) {
                    properties.add("app.http.defaults.concurrency.default-request-count=" + concurrency.getDefaultRequestCount());
                    properties.add("app.api.concurrency.default-request-count=" + concurrency.getDefaultRequestCount());
                }
            }

            if (defaults != null && defaults.getBaseUrl() != null) {
                properties.add("app.http.defaults.base-url=" + defaults.getBaseUrl());
            }

            if (httpConfig.getServices() != null) {
                httpConfig.getServices().forEach((serviceId, serviceProperties) -> {
                    if (serviceProperties == null) {
                        return;
                    }
                    String prefix = "app.http.services." + serviceId + ".";
                    String baseUrl = valueAsString(serviceProperties.get("baseUrl"));
                    if (baseUrl != null) {
                        properties.add(prefix + "base-url=" + baseUrl);
                        if ("fapi".equals(serviceId)) {
                            properties.add("app.api.fapi.base-url=" + baseUrl);
                        }
                        if ("manager".equals(serviceId)) {
                            properties.add("app.api.manager.base-url=" + baseUrl);
                        }
                        if ("cap".equals(serviceId)) {
                            properties.add("app.api.cap.base-url=" + baseUrl);
                        }
                    }


                });
            }
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

                properties.add("redis.aggregate.max-gambling-count=" + aggregateConfig.maxGamblingCount());
                properties.add("redis.aggregate.max-iframe-count=" + aggregateConfig.maxIframeCount());
                properties.add("redis.aggregate.retry-attempts=" + aggregateConfig.retryAttempts());
                properties.add("redis.aggregate.retry-delay-ms=" + aggregateConfig.retryDelayMs());
            }

            if (redisProperties.getClients() != null) {
                redisProperties.getClients().forEach((clientName, clientConfig) -> {
                    if (clientConfig.getHost() != null) {
                        properties.add("redis.clients." + clientName + ".host=" + clientConfig.getHost());
                    }
                    properties.add("redis.clients." + clientName + ".port=" + clientConfig.getPort());
                    properties.add("redis.clients." + clientName + ".database=" + clientConfig.getDatabase());

                    if (clientConfig.getPassword() != null) {
                        properties.add("redis.clients." + clientName + ".password=" + clientConfig.getPassword());
                    }

                    Duration timeout = clientConfig.getTimeout();
                    if (timeout != null) {
                        properties.add("redis.clients." + clientName + ".timeout=" + formatDuration(timeout));
                    }

                    RedisInstanceProperties.LettucePoolProperties poolConfig = clientConfig.getLettucePool();
                    if (poolConfig != null) {
                        if (poolConfig.getMaxActive() != null) {
                            properties.add("redis.clients." + clientName + ".lettuce-pool.max-active=" + poolConfig.getMaxActive());
                        }
                        if (poolConfig.getMaxIdle() != null) {
                            properties.add("redis.clients." + clientName + ".lettuce-pool.max-idle=" + poolConfig.getMaxIdle());
                        }
                        if (poolConfig.getMinIdle() != null) {
                            properties.add("redis.clients." + clientName + ".lettuce-pool.min-idle=" + poolConfig.getMinIdle());
                        }
                        Duration maxWait = poolConfig.getMaxWait();
                        if (maxWait != null) {
                            properties.add("redis.clients." + clientName + ".lettuce-pool.max-wait=" + formatDuration(maxWait));
                        }
                        Duration shutdownTimeout = poolConfig.getShutdownTimeout();
                        if (shutdownTimeout != null) {
                            properties.add("redis.clients." + clientName + ".lettuce-pool.shutdown-timeout=" + formatDuration(shutdownTimeout));
                        }
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

    private static String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }


}
