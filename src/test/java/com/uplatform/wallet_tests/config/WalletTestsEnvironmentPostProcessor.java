package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testing.multisource.config.EnvironmentConfig;
import com.testing.multisource.config.EnvironmentConfigPostProcessor;
import com.testing.multisource.config.modules.http.HttpModuleProperties;

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

        PlatformConfig overridesPlatform = walletTests.getPlatform();
        if (config.getPlatform() == null && overridesPlatform != null) {
            EnvironmentConfig.PlatformConfig platform = new EnvironmentConfig.PlatformConfig();
            platform.setCurrency(overridesPlatform.getCurrency());
            platform.setCountry(overridesPlatform.getCountry());
            platform.setNodeId(overridesPlatform.getNodeId());
            platform.setGroupId(overridesPlatform.getGroupId());
            config.setPlatform(platform);
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

        Map<String, Map<String, Object>> services = http.getServices();
        if (services == null) {
            services = new LinkedHashMap<>();
            http.setServices(services);
        }

        final Map<String, Map<String, Object>> targetServices = services;

        httpOverrides.getServices().forEach((serviceId, override) -> {
            if (override == null) {
                return;
            }

            Map<String, Object> service = targetServices.computeIfAbsent(serviceId, id -> new LinkedHashMap<>());

            if (override.getSecret() != null) {
                service.put("secret", override.getSecret());
            }
            if (override.getCasinoId() != null) {
                service.put("casinoId", override.getCasinoId());
            }

            Credentials overridesCredentials = override.getCredentials();
            if (overridesCredentials != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> credentials = Optional.ofNullable((Map<String, Object>) service.get("credentials"))
                        .map(existing -> new LinkedHashMap<>(existing))
                        .orElseGet(LinkedHashMap::new);

                if (overridesCredentials.getUsername() != null) {
                    credentials.put("username", overridesCredentials.getUsername());
                }
                if (overridesCredentials.getPassword() != null) {
                    credentials.put("password", overridesCredentials.getPassword());
                }

                service.put("credentials", credentials);
            }
        });
    }
}
