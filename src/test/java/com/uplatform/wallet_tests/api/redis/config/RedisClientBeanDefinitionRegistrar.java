package com.uplatform.wallet_tests.api.redis.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import com.uplatform.wallet_tests.api.redis.GenericRedisClient;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Dynamically registers Redis infrastructure beans (connection factories, templates and fluent clients)
 * based on the declarative configuration located under {@code redis.*} properties.
 */
public class RedisClientBeanDefinitionRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        RedisModuleProperties properties = bindProperties();
        if (properties == null) {
            return;
        }

        registerInfrastructureBeans(registry, properties.getInstances());
        registerClientBeans(registry, properties.getClients());
    }

    private RedisModuleProperties bindProperties() {
        try {
            return Binder.get(environment)
                    .bind("redis", Bindable.of(RedisModuleProperties.class))
                    .orElse(null);
        } catch (BindException ex) {
            throw new IllegalStateException("Failed to bind redis configuration", ex);
        }
    }

    private void registerInfrastructureBeans(BeanDefinitionRegistry registry,
                                              Map<String, RedisInstanceProperties> instances) {
        if (instances == null) {
            return;
        }
        instances.forEach((name, props) -> {
            String connectionBeanName = connectionFactoryBeanName(name);
            if (!registry.containsBeanDefinition(connectionBeanName)) {
                registerConnectionFactory(registry, connectionBeanName, props);
            }
            String templateBeanName = templateBeanName(name);
            if (!registry.containsBeanDefinition(templateBeanName)) {
                registerTemplate(registry, templateBeanName, connectionBeanName);
            }
        });
    }

    private void registerClientBeans(BeanDefinitionRegistry registry,
                                     Map<String, RedisModuleProperties.RedisClientProperties> clients) {
        if (clients == null) {
            return;
        }
        clients.forEach((name, props) -> {
            if (!StringUtils.hasText(props.getInstanceRef())) {
                throw new IllegalStateException("Redis client '" + name + "' must define instance-ref");
            }
            if (props.getDataType() == null) {
                throw new IllegalStateException("Redis client '" + name + "' must define data-type");
            }
            String beanName = clientBeanName(name);
            if (registry.containsBeanDefinition(beanName)) {
                return;
            }

            String templateBeanName = templateBeanName(props.getInstanceRef());
            if (!registry.containsBeanDefinition(templateBeanName)) {
                throw new IllegalStateException("Redis client '" + name + "' references unknown instance '"
                        + props.getInstanceRef() + "'");
            }

            BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(GenericRedisClient.class);
            builder.setAutowireMode(GenericBeanDefinition.AUTOWIRE_CONSTRUCTOR);
            builder.addConstructorArgValue(beanName);
            builder.addConstructorArgValue(props.getInstanceRef());
            builder.addConstructorArgReference(templateBeanName);
            builder.addConstructorArgValue(props.getDataType());
            builder.addConstructorArgReference("redisTypeMappingRegistry");
            builder.addConstructorArgReference("objectMapper");
            builder.addConstructorArgReference("allureAttachmentService");
            builder.addConstructorArgReference("redisAwaitilityProperties");

            RootBeanDefinition beanDefinition = (RootBeanDefinition) builder.getBeanDefinition();
            beanDefinition.setTargetType(ResolvableType.forClass(GenericRedisClient.class));
            registry.registerBeanDefinition(beanName, beanDefinition);
        });
    }

    private void registerConnectionFactory(BeanDefinitionRegistry registry,
                                           String beanName,
                                           RedisInstanceProperties props) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        standalone.setHostName(props.getHost());
        standalone.setPort(props.getPort());
        standalone.setDatabase(props.getDatabase());
        if (StringUtils.hasText(props.getPassword())) {
            standalone.setPassword(props.getPassword());
        }

        LettucePoolingClientConfiguration clientConfig = createClientConfiguration(props);

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(LettuceConnectionFactory.class);
        builder.addConstructorArgValue(standalone);
        builder.addConstructorArgValue(clientConfig);
        builder.setDestroyMethodName("destroy");
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }

    private LettucePoolingClientConfiguration createClientConfiguration(RedisInstanceProperties props) {
        RedisInstanceProperties.LettucePoolProperties poolProps = props.getLettucePool();
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        if (poolProps.getMaxActive() != null) {
            poolConfig.setMaxTotal(poolProps.getMaxActive());
        }
        if (poolProps.getMaxIdle() != null) {
            poolConfig.setMaxIdle(poolProps.getMaxIdle());
        }
        if (poolProps.getMinIdle() != null) {
            poolConfig.setMinIdle(poolProps.getMinIdle());
        }
        if (poolProps.getMaxWait() != null) {
            poolConfig.setMaxWait(poolProps.getMaxWait());
        }

        Duration commandTimeout = props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(60);
        Duration shutdownTimeout = poolProps.getShutdownTimeout() != null
                ? poolProps.getShutdownTimeout()
                : Duration.ofMillis(100);

        return LettucePoolingClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .shutdownTimeout(shutdownTimeout)
                .poolConfig(poolConfig)
                .build();
    }

    private void registerTemplate(BeanDefinitionRegistry registry,
                                   String templateBeanName,
                                   String connectionFactoryBeanName) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RedisTemplate.class);
        builder.addPropertyReference("connectionFactory", connectionFactoryBeanName);
        StringRedisSerializer serializer = new StringRedisSerializer();
        builder.addPropertyValue("keySerializer", serializer);
        builder.addPropertyValue("valueSerializer", serializer);
        builder.addPropertyValue("hashKeySerializer", serializer);
        builder.addPropertyValue("hashValueSerializer", serializer);
        builder.setInitMethodName("afterPropertiesSet");
        registry.registerBeanDefinition(templateBeanName, builder.getBeanDefinition());
    }

    private String connectionFactoryBeanName(String instanceName) {
        return instanceName + "RedisConnectionFactory";
    }

    private String templateBeanName(String instanceName) {
        return instanceName + "RedisTemplate";
    }

    private String clientBeanName(String clientName) {
        String normalized = normalizeClientName(clientName);
        return "redis" + normalized + "Client";
    }

    private String normalizeClientName(String clientName) {
        if (!StringUtils.hasText(clientName)) {
            return "Client";
        }
        String[] parts = clientName.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                builder.append(StringUtils.capitalize(part));
            }
        }
        if (builder.length() == 0) {
            builder.append(StringUtils.capitalize(clientName));
        }
        return builder.toString();
    }

    @Override
    public void postProcessBeanFactory(
            org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}

