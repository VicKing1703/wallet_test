package com.testing.multisource.api.redis.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Collections;
import java.util.Map;

public class RedisClientsConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        if (environment == null) {
            return false;
        }
        try {
            Map<String, Object> clients = Binder.get(environment)
                    .bind("redis.clients", Bindable.mapOf(String.class, Object.class))
                    .orElse(Collections.emptyMap());
            return !clients.isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }
}
