package com.uplatform.wallet_tests.api.redis.config;

import lombok.Data;

import java.time.Duration;

@Data
public class RedisInstanceProperties {

    private String host;
    private int port = 6379;
    private int database = 0;
    private String password;
    private Duration timeout = Duration.ofSeconds(60);
    private LettucePoolProperties lettucePool = new LettucePoolProperties();

    @Data
    public static class LettucePoolProperties {
        private Integer maxActive;
        private Integer maxIdle;
        private Integer minIdle;
        private Duration maxWait;
        private Duration shutdownTimeout = Duration.ofMillis(100);
    }
}
