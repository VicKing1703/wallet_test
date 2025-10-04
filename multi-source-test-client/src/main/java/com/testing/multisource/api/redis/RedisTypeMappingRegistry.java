package com.testing.multisource.api.redis;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class RedisTypeMappingRegistry {

    private final Map<String, TypeReference<?>> mappings = new LinkedHashMap<>();

    public RedisTypeMappingRegistry register(String clientName, TypeReference<?> reference) {
        if (!StringUtils.hasText(clientName)) {
            throw new IllegalArgumentException("Redis client name must not be blank");
        }
        mappings.put(clientName, Objects.requireNonNull(reference, "reference"));
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> TypeReference<T> resolve(String clientName) {
        if (!StringUtils.hasText(clientName)) {
            throw new IllegalArgumentException("Redis client name must not be blank");
        }
        TypeReference<?> reference = mappings.get(clientName);
        if (reference == null) {
            throw new IllegalArgumentException("Redis type mapping is not defined for client '" + clientName + "'");
        }
        return (TypeReference<T>) reference;
    }
}

