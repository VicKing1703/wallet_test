package com.testing.multisource.config.modules.http;

import java.util.Map;

public class HttpServiceHelper {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getService(HttpModuleProperties http, String serviceId) {
        if (http == null || http.getServices() == null) {
            return null;
        }
        return (Map<String, Object>) http.getServices().get(serviceId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getCredentials(Map<String, Object> serviceConfig) {
        if (serviceConfig == null) {
            return null;
        }
        return (Map<String, Object>) serviceConfig.get("credentials");
    }

    public static String getManagerCasinoId(HttpModuleProperties http) {
        Map<String, Object> managerService = getService(http, "manager");
        if (managerService != null) {
            Object casinoId = managerService.get("casinoId");
            return casinoId != null ? casinoId.toString() : null;
        }
        return null;
    }

    public static String getManagerSecret(HttpModuleProperties http) {
        Map<String, Object> managerService = getService(http, "manager");
        if (managerService != null) {
            Object secret = managerService.get("secret");
            return secret != null ? secret.toString() : null;
        }
        return null;
    }

    public static String getCapUsername(HttpModuleProperties http) {
        Map<String, Object> capService = getService(http, "cap");
        Map<String, Object> credentials = getCredentials(capService);
        if (credentials != null) {
            Object username = credentials.get("username");
            return username != null ? username.toString() : null;
        }
        return null;
    }

    public static String getCapPassword(HttpModuleProperties http) {
        Map<String, Object> capService = getService(http, "cap");
        Map<String, Object> credentials = getCredentials(capService);
        if (credentials != null) {
            Object password = credentials.get("password");
            return password != null ? password.toString() : null;
        }
        return null;
    }

    public static String getCapPlatformUserId(HttpModuleProperties http) {
        Map<String, Object> capService = getService(http, "cap");
        if (capService != null) {
            Object platformUserId = capService.get("platformUserId");
            return platformUserId != null ? platformUserId.toString() : null;
        }
        return null;
    }

    public static String getCapPlatformUsername(HttpModuleProperties http) {
        Map<String, Object> capService = getService(http, "cap");
        if (capService != null) {
            Object platformUsername = capService.get("platformUsername");
            if (platformUsername != null) {
                return platformUsername.toString();
            }
        }
        return getCapUsername(http);
    }
}
