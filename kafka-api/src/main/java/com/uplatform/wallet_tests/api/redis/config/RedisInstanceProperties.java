package com.uplatform.wallet_tests.api.redis.config;

import java.time.Duration;

public class RedisInstanceProperties {

    private String host;
    private int port = 6379;
    private int database = 0;
    private String password;
    private Duration timeout = Duration.ofSeconds(60);
    private LettucePoolProperties lettucePool = new LettucePoolProperties();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public LettucePoolProperties getLettucePool() {
        return lettucePool;
    }

    public void setLettucePool(LettucePoolProperties lettucePool) {
        this.lettucePool = lettucePool;
    }

    public static class LettucePoolProperties {
        private Integer maxActive;
        private Integer maxIdle;
        private Integer minIdle;
        private Duration maxWait;
        private Duration shutdownTimeout = Duration.ofMillis(100);

        public Integer getMaxActive() {
            return maxActive;
        }

        public void setMaxActive(Integer maxActive) {
            this.maxActive = maxActive;
        }

        public Integer getMaxIdle() {
            return maxIdle;
        }

        public void setMaxIdle(Integer maxIdle) {
            this.maxIdle = maxIdle;
        }

        public Integer getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(Integer minIdle) {
            this.minIdle = minIdle;
        }

        public Duration getMaxWait() {
            return maxWait;
        }

        public void setMaxWait(Duration maxWait) {
            this.maxWait = maxWait;
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }
    }
}

