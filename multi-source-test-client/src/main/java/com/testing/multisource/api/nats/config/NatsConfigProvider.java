package com.testing.multisource.api.nats.config;

import com.testing.multisource.config.modules.nats.NatsConfig;

public interface NatsConfigProvider {
    NatsConfig getNatsConfig();
    String getNatsStreamPrefix();
}
