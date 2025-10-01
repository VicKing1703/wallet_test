package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface EnvironmentConfigPostProcessor {
    void postProcess(EnvironmentConfig config, ObjectNode rawConfig, ObjectMapper mapper);
}
