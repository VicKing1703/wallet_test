package com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Environment enumeration providing base URL per env name.
 */
@Getter
@RequiredArgsConstructor
public enum EnvironmentHost {
    BETA_09("beta-09", "https://beta-09.b2bdev.pro");

    private final String envName;
    private final String baseUrl;

    public static EnvironmentHost fromEnv(String env) {
        return Arrays.stream(values())
                .filter(h -> h.envName.equalsIgnoreCase(env))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown environment: " + env));
    }

    public static EnvironmentHost current() {
        return fromEnv(System.getProperty("env"));
    }
}
