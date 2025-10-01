package com.uplatform.wallet_tests.api.http.config;

import lombok.Data;

@Data
public class HttpServiceProperties {
    private String baseUrl;
    private HttpServiceCredentials credentials;
    private String secret;
    private String casinoId;
}
