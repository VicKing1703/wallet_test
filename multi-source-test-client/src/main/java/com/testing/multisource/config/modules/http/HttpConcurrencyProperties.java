package com.testing.multisource.config.modules.http;

import lombok.Data;

@Data
public class HttpConcurrencyProperties {
    private Long requestTimeoutMs;
    private Integer defaultRequestCount;
}
