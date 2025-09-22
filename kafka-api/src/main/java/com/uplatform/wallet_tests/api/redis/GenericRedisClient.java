package com.uplatform.wallet_tests.api.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.uplatform.wallet_tests.api.redis.config.RedisAwaitilityProperties;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;

public class GenericRedisClient<T> {

    private final String beanName;
    private final String instanceName;
    private final RedisTemplate<String, String> redisTemplate;
    private final TypeReference<T> typeReference;
    private final ObjectMapper objectMapper;
    private final AllureAttachmentService attachmentService;
    private final RedisAwaitilityProperties awaitilityProperties;

    public GenericRedisClient(String beanName,
                              String instanceName,
                              RedisTemplate<String, String> redisTemplate,
                              RedisTypeMappingRegistry typeMappingRegistry,
                              ObjectMapper objectMapper,
                              AllureAttachmentService attachmentService,
                              RedisAwaitilityProperties awaitilityProperties) {
        this.beanName = Objects.requireNonNull(beanName, "beanName");
        this.instanceName = Objects.requireNonNull(instanceName, "instanceName");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.typeReference = typeMappingRegistry.resolve(instanceName);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.attachmentService = Objects.requireNonNull(attachmentService, "attachmentService");
        this.awaitilityProperties = Objects.requireNonNull(awaitilityProperties, "awaitilityProperties");
    }

    public RedisExpectationBuilder<T> key(String key) {
        return new RedisExpectationBuilder<>(
                beanName,
                instanceName,
                key,
                redisTemplate,
                typeReference,
                objectMapper,
                attachmentService,
                awaitilityProperties
        );
    }
}

