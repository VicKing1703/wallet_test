package com.testing.multisource.api.http.config;

import com.testing.multisource.config.modules.http.HttpModuleProperties;

public interface HttpConfigProvider {
    HttpModuleProperties getHttpConfig();
}
