package com.uplatform.wallet_tests.api.http.config;

import com.uplatform.wallet_tests.config.ApiConfig;
import com.uplatform.wallet_tests.config.ConcurrencyConfig;
import com.uplatform.wallet_tests.config.Credentials;
import com.uplatform.wallet_tests.config.ManagerConfig;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Data
public class HttpModuleProperties {
    private HttpDefaultsProperties defaults = new HttpDefaultsProperties();
    private Map<String, HttpServiceProperties> services = new LinkedHashMap<>();

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

        Map<String, HttpServiceProperties> services = new LinkedHashMap<>();

        HttpServiceProperties fapi = new HttpServiceProperties();
        fapi.setBaseUrl(legacy.getBaseUrl());
        services.put("fapi", fapi);

        HttpServiceProperties manager = new HttpServiceProperties();
        manager.setBaseUrl(legacy.getBaseUrl());
        if (legacy.getManager() != null) {
            manager.setSecret(legacy.getManager().getSecret());
            manager.setCasinoId(legacy.getManager().getCasinoId());
        }
        services.put("manager", manager);

        HttpServiceProperties cap = new HttpServiceProperties();
        cap.setBaseUrl(resolveCapBaseUrl(legacy.getBaseUrl()));
        if (legacy.getCapCredentials() != null) {
            HttpServiceCredentials credentials = new HttpServiceCredentials();
            credentials.setUsername(legacy.getCapCredentials().getUsername());
            credentials.setPassword(legacy.getCapCredentials().getPassword());
            cap.setCredentials(credentials);
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

        Map<String, HttpServiceProperties> services = Optional.ofNullable(getServices()).orElseGet(LinkedHashMap::new);

        HttpServiceProperties managerService = services.get("manager");
        if (managerService != null) {
            if (apiConfig.getBaseUrl() == null) {
                apiConfig.setBaseUrl(managerService.getBaseUrl());
            }
            ManagerConfig managerConfig = new ManagerConfig();
            managerConfig.setSecret(managerService.getSecret());
            managerConfig.setCasinoId(managerService.getCasinoId());
            apiConfig.setManager(managerConfig);
        }

        HttpServiceProperties capService = services.get("cap");
        if (capService != null) {
            if (apiConfig.getBaseUrl() == null) {
                apiConfig.setBaseUrl(capService.getBaseUrl());
            }
            if (capService.getCredentials() != null) {
                Credentials credentials = new Credentials();
                credentials.setUsername(capService.getCredentials().getUsername());
                credentials.setPassword(capService.getCredentials().getPassword());
                apiConfig.setCapCredentials(credentials);
            }
        }

        if (apiConfig.getBaseUrl() == null) {
            HttpServiceProperties fapiService = services.get("fapi");
            if (fapiService != null) {
                apiConfig.setBaseUrl(fapiService.getBaseUrl());
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
}
