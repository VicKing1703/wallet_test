package com.testing.multisource.config.modules.nats;

import java.util.List;

public record NatsConnectionConfig(
        List<String> hosts,
        long connectReconnectWaitSeconds,
        int connectMaxReconnects
) {}
