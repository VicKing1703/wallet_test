package com.uplatform.wallet_tests.api.http.config;

import com.uplatform.wallet_tests.config.ApiConfig;
import com.uplatform.wallet_tests.config.ConcurrencyConfig;
import com.uplatform.wallet_tests.config.Credentials;
import com.uplatform.wallet_tests.config.ManagerConfig;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Data
public class HttpModuleProperties {
    private HttpDefaultsProperties defaults = new HttpDefaultsProperties();
    private Map<String, Map<String, Object>> services = new LinkedHashMap<>();

    public static HttpModuleProperties fromLegacy(ApiConfig legacy) {
        if (legacy == null) {
            return null;
        }
        HttpModuleProperties module = new HttpModuleProperties();

        HttpDefaultsProperties defaults = new HttpDefaultsProperties();
        defaults.setBaseUrl(legacy.getBaseUrl());
        if (legacy.getConcurrency() != null) {
            HttpConcurrencyProperties concurrency = new HttpConcurrencyProperties();
            concurrency.setRequestTimeoutMs((long) legacy.getConcurrency().getRequestTimeoutMs());
            concurrency.setDefaultRequestCount(legacy.getConcurrency().getDefaultRequestCount());
            defaults.setConcurrency(concurrency);
        }
        module.setDefaults(defaults);

        Map<String, Map<String, Object>> services = new LinkedHashMap<>();

        Map<String, Object> fapi = new LinkedHashMap<>();
        fapi.put("baseUrl", legacy.getBaseUrl());
        services.put("fapi", fapi);

        Map<String, Object> manager = new LinkedHashMap<>();
        manager.put("baseUrl", legacy.getBaseUrl());
        if (legacy.getManager() != null) {
            putIfNotNull(manager, "secret", legacy.getManager().getSecret());
            putIfNotNull(manager, "casinoId", legacy.getManager().getCasinoId());
        }
        services.put("manager", manager);

        Map<String, Object> cap = new LinkedHashMap<>();
        cap.put("baseUrl", resolveCapBaseUrl(legacy.getBaseUrl()));
        if (legacy.getCapCredentials() != null) {
            Map<String, Object> credentials = new LinkedHashMap<>();
            putIfNotNull(credentials, "username", legacy.getCapCredentials().getUsername());
            putIfNotNull(credentials, "password", legacy.getCapCredentials().getPassword());
            cap.put("credentials", credentials);
        }
        services.put("cap", cap);

        module.setServices(services);
        return module;
    }

    public ApiConfig toLegacyApiConfig() {
        ApiConfig apiConfig = new ApiConfig();
        HttpDefaultsProperties defaults = Optional.ofNullable(getDefaults()).orElseGet(HttpDefaultsProperties::new);
        apiConfig.setBaseUrl(defaults.getBaseUrl());

        HttpConcurrencyProperties concurrency = defaults.getConcurrency();
        if (concurrency != null && (concurrency.getRequestTimeoutMs() != null || concurrency.getDefaultRequestCount() != null)) {
            ConcurrencyConfig concurrencyConfig = new ConcurrencyConfig();
            if (concurrency.getRequestTimeoutMs() != null) {
                concurrencyConfig.setRequestTimeoutMs(concurrency.getRequestTimeoutMs());
            }
            if (concurrency.getDefaultRequestCount() != null) {
                concurrencyConfig.setDefaultRequestCount(concurrency.getDefaultRequestCount());
            }
            apiConfig.setConcurrency(concurrencyConfig);
        }

        Map<String, Map<String, Object>> services = Optional.ofNullable(getServices()).orElseGet(LinkedHashMap::new);

        Map<String, Object> managerService = services.get("manager");
        if (managerService != null) {
            if (apiConfig.getBaseUrl() == null) {
                apiConfig.setBaseUrl(asString(managerService.get("baseUrl")));
            }
            if (managerService.containsKey("secret") || managerService.containsKey("casinoId")) {
                ManagerConfig managerConfig = new ManagerConfig();
                managerConfig.setSecret(asString(managerService.get("secret")));
                managerConfig.setCasinoId(asString(managerService.get("casinoId")));
                apiConfig.setManager(managerConfig);
            }
        }

        Map<String, Object> capService = services.get("cap");
        if (capService != null) {
            if (apiConfig.getBaseUrl() == null) {
                apiConfig.setBaseUrl(asString(capService.get("baseUrl")));
            }
            Map<String, Object> credentialsMap = asMap(capService.get("credentials"));
            if (credentialsMap != null && (!credentialsMap.isEmpty())) {
                Credentials credentials = new Credentials();
                credentials.setUsername(asString(credentialsMap.get("username")));
                credentials.setPassword(asString(credentialsMap.get("password")));
                apiConfig.setCapCredentials(credentials);
            }
        }

        if (apiConfig.getBaseUrl() == null) {
            Map<String, Object> fapiService = services.get("fapi");
            if (fapiService != null) {
                apiConfig.setBaseUrl(asString(fapiService.get("baseUrl")));
            }
        }
        return apiConfig;
    }

    private static String resolveCapBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String lowerCase = baseUrl.toLowerCase(Locale.ROOT);
        String protocol = lowerCase.startsWith("https://") ? "https://" : lowerCase.startsWith("http://") ? "http://" : "";
        String withoutProtocol = protocol.isEmpty() ? baseUrl : baseUrl.substring(protocol.length());
        return protocol + "cap." + withoutProtocol;
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(Objects.toString(k, null), v));
            return result;
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
