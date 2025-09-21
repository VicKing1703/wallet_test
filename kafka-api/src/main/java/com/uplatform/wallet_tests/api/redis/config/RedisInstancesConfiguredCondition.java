package com.uplatform.wallet_tests.api.redis.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Collections;
import java.util.Map;

/**
 * Activates Redis auto-configuration only when at least one Redis instance is configured.
 */
public class RedisInstancesConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        if (environment == null) {
            return false;
        }
        try {
            Map<String, Object> instances = Binder.get(environment)
                    .bind("redis.instances", Bindable.mapOf(String.class, Object.class))
                    .orElse(Collections.emptyMap());
            return !instances.isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }
}
