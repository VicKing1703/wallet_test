package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uplatform.wallet_tests.api.http.config.HttpModuleProperties;
import com.uplatform.wallet_tests.api.http.config.HttpServiceCredentials;
import com.uplatform.wallet_tests.api.http.config.HttpServiceProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class WalletTestsEnvironmentPostProcessor implements EnvironmentConfigPostProcessor {

    @Override
    public void postProcess(EnvironmentConfig config, ObjectNode rawConfig, ObjectMapper mapper) {
        if (rawConfig == null) {
            return;
        }

        ObjectNode walletNode = Optional.ofNullable(rawConfig.get("walletTests"))
                .filter(ObjectNode.class::isInstance)
                .map(ObjectNode.class::cast)
                .orElse(null);

        if (walletNode == null) {
            return;
        }

        WalletTestsConfig walletTests = mapper.convertValue(walletNode, WalletTestsConfig.class);

        if (config.getPlatform() == null && walletTests.getPlatform() != null) {
            config.setPlatform(walletTests.getPlatform());
        }

        WalletHttpOverrides httpOverrides = walletTests.getHttp();
        if (httpOverrides == null || httpOverrides.getServices() == null || httpOverrides.getServices().isEmpty()) {
            return;
        }

        HttpModuleProperties http = config.getHttp();
        if (http == null) {
            http = new HttpModuleProperties();
            config.setHttp(http);
        }

        Map<String, HttpServiceProperties> services = http.getServices();
        if (services == null) {
            services = new LinkedHashMap<>();
            http.setServices(services);
        }

        httpOverrides.getServices().forEach((serviceId, override) -> {
            if (override == null) {
                return;
            }

            HttpServiceProperties service = services.computeIfAbsent(serviceId, id -> new HttpServiceProperties());

            if (override.getSecret() != null) {
                service.setSecret(override.getSecret());
            }
            if (override.getCasinoId() != null) {
                service.setCasinoId(override.getCasinoId());
            }

            HttpServiceCredentials overridesCredentials = override.getCredentials();
            if (overridesCredentials != null) {
                HttpServiceCredentials credentials = Optional.ofNullable(service.getCredentials())
                        .orElseGet(HttpServiceCredentials::new);

                if (overridesCredentials.getUsername() != null) {
                    credentials.setUsername(overridesCredentials.getUsername());
                }
                if (overridesCredentials.getPassword() != null) {
                    credentials.setPassword(overridesCredentials.getPassword());
                }

                service.setCredentials(credentials);
            }
        });
    }
}
