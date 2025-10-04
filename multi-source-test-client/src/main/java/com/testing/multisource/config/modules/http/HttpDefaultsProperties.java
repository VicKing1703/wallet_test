package com.testing.multisource.config.modules.http;

import lombok.Data;

@Data
public class HttpDefaultsProperties {
    private String baseUrl;
    private HttpConcurrencyProperties concurrency = new HttpConcurrencyProperties();
}
