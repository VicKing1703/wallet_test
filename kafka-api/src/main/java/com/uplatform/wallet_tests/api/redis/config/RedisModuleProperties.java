package com.uplatform.wallet_tests.api.redis.config;

import com.uplatform.wallet_tests.config.RedisAggregateConfig;

import java.util.LinkedHashMap;
import java.util.Map;
public class RedisModuleProperties {

    private RedisAggregateConfig aggregate;
    private Map<String, RedisClientProperties> clients = new LinkedHashMap<>();

    public RedisAggregateConfig getAggregate() {
        return aggregate;
    }

    public void setAggregate(RedisAggregateConfig aggregate) {
        this.aggregate = aggregate;
    }

    public Map<String, RedisClientProperties> getClients() {
        return clients;
    }

    public void setClients(Map<String, RedisClientProperties> clients) {
        this.clients = clients;
    }

    public static class RedisClientProperties extends RedisInstanceProperties {
    }
}

