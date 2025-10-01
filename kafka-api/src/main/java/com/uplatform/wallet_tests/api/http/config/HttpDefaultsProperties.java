package com.uplatform.wallet_tests.api.http.config;

import lombok.Data;

@Data
public class HttpDefaultsProperties {
    private String baseUrl;
    private HttpConcurrencyProperties concurrency = new HttpConcurrencyProperties();
}
