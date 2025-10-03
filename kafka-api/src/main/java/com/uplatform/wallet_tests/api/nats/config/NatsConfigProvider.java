package com.uplatform.wallet_tests.api.nats.config;

import com.uplatform.wallet_tests.config.modules.nats.NatsConfig;

public interface NatsConfigProvider {
    NatsConfig getNatsConfig();
    String getNatsStreamPrefix();
}
