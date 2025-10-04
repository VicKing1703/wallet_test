package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testing.multisource.config.EnvironmentConfigurationProvider;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Configures Spring properties from walletTests section of environment config.
 * This section contains test-specific configuration like credentials, secrets, and platform settings.
 */
public class WalletTestsPropertiesConfigurator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final EnvironmentConfigurationProvider provider = new EnvironmentConfigurationProvider();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        try {
            provider.loadConfig();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load environment configuration during class initialization", e);
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        JsonNode rawConfig = provider.getRawEnvironmentNode();

        if (rawConfig == null || !rawConfig.has("walletTests")) {
            return;
        }

        List<String> properties = new ArrayList<>();

        try {
            WalletTestsConfig walletTests = objectMapper.treeToValue(rawConfig.get("walletTests"), WalletTestsConfig.class);

            if (walletTests == null) {
                return;
            }

            // Process HTTP service overrides (credentials, secrets, casinoId)
            WalletHttpOverrides httpOverrides = walletTests.getHttp();
            if (httpOverrides != null && httpOverrides.getServices() != null) {
                httpOverrides.getServices().forEach((serviceId, serviceOverride) -> {
                    if (serviceOverride == null) {
                        return;
                    }

                    // Automatically register credentials for any service
                    Credentials credentials = serviceOverride.getCredentials();
                    if (credentials != null) {
                        if (credentials.getUsername() != null) {
                            properties.add("app.api." + serviceId + ".credentials.username=" + credentials.getUsername());
                        }
                        if (credentials.getPassword() != null) {
                            properties.add("app.api." + serviceId + ".credentials.password=" + credentials.getPassword());
                        }
                    }

                    // Automatically register secret for any service
                    if (serviceOverride.getSecret() != null) {
                        properties.add("app.api." + serviceId + ".secret=" + serviceOverride.getSecret());
                    }

                    // Automatically register casinoId for any service
                    if (serviceOverride.getCasinoId() != null) {
                        properties.add("app.api." + serviceId + ".casino-id=" + serviceOverride.getCasinoId());
                    }
                });
            }

            // Process platform configuration
            PlatformConfig platform = walletTests.getPlatform();
            if (platform != null) {
                if (platform.getNodeId() != null) {
                    properties.add("app.settings.default.platform-node-id=" + platform.getNodeId());
                }
                if (platform.getGroupId() != null) {
                    properties.add("app.settings.default.platform-group-id=" + platform.getGroupId());
                }
                if (platform.getCurrency() != null) {
                    properties.add("app.settings.default.currency=" + platform.getCurrency());
                }
                if (platform.getCountry() != null) {
                    properties.add("app.settings.default.country=" + platform.getCountry());
                }
            }

            if (!properties.isEmpty()) {
                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, properties.toArray(new String[0]));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to process walletTests configuration", e);
        }
    }
}
