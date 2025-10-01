package com.uplatform.wallet_tests.api.http.config;

import lombok.Data;

@Data
public class HttpConcurrencyProperties {
    private Long requestTimeoutMs;
    private Integer defaultRequestCount;
}
