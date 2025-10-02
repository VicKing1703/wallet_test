package com.uplatform.wallet_tests.config;

import lombok.Data;

@Data
public class ApiConfig {
    private String baseUrl;
    private Credentials capCredentials;
    private ManagerConfig manager;
    private ConcurrencyConfig concurrency;

    @Data
    public static class Credentials {
        private String username;
        private String password;
    }

    @Data
    public static class ManagerConfig {
        private String secret;
        private String casinoId;
    }
}
