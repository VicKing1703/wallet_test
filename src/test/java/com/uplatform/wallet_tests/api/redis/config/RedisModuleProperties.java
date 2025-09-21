package com.uplatform.wallet_tests.api.redis.config;

import com.uplatform.wallet_tests.api.redis.RedisDataType;

import java.util.LinkedHashMap;
import java.util.Map;

public class RedisModuleProperties {

    private Map<String, RedisInstanceProperties> instances = new LinkedHashMap<>();
    private Map<String, RedisClientProperties> clients = new LinkedHashMap<>();

    public Map<String, RedisInstanceProperties> getInstances() {
        return instances;
    }

    public void setInstances(Map<String, RedisInstanceProperties> instances) {
        this.instances = instances;
    }

    public Map<String, RedisClientProperties> getClients() {
        return clients;
    }

    public void setClients(Map<String, RedisClientProperties> clients) {
        this.clients = clients;
    }

    public static class RedisClientProperties {
        private String instanceRef;
        private RedisDataType dataType;

        public String getInstanceRef() {
            return instanceRef;
        }

        public void setInstanceRef(String instanceRef) {
            this.instanceRef = instanceRef;
        }

        public RedisDataType getDataType() {
            return dataType;
        }

        public void setDataType(RedisDataType dataType) {
            this.dataType = dataType;
        }
    }
}

