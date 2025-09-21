package com.uplatform.wallet_tests.api.redis;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry that maps logical {@link RedisDataType} values to Jackson {@link TypeReference}s.
 * It allows the fluent Redis client to work with complex generic payload types.
 */
public class RedisTypeMappingRegistry {

    private final Map<RedisDataType, TypeReference<?>> mappings = new EnumMap<>(RedisDataType.class);

    public RedisTypeMappingRegistry register(RedisDataType dataType, TypeReference<?> reference) {
        mappings.put(dataType, reference);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> TypeReference<T> resolve(RedisDataType dataType) {
        TypeReference<?> reference = mappings.get(dataType);
        if (reference == null) {
            throw new IllegalArgumentException("Redis data type mapping is not defined for " + dataType);
        }
        return (TypeReference<T>) reference;
    }
}

