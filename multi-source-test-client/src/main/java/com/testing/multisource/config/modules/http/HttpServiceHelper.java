package com.testing.multisource.config.modules.http;

import java.util.Map;

public class HttpServiceHelper {

    

    @SuppressWarnings("unchecked")
    public static String getManagerCasinoId(HttpModuleProperties http) {
        if (http == null || http.getServices() == null) {
            return null;
        }
        Map<String, Object> managerService = (Map<String, Object>) http.getServices().get("manager");
        if (managerService != null) {
            Object casinoId = managerService.get("casinoId");
            return casinoId != null ? casinoId.toString() : null;
        }
        return null;
    }

    

    @SuppressWarnings("unchecked")
    public static String getManagerSecret(HttpModuleProperties http) {
        if (http == null || http.getServices() == null) {
            return null;
        }
        Map<String, Object> managerService = (Map<String, Object>) http.getServices().get("manager");
        if (managerService != null) {
            Object secret = managerService.get("secret");
            return secret != null ? secret.toString() : null;
        }
        return null;
    }

    

    @SuppressWarnings("unchecked")
    public static String getCapUsername(HttpModuleProperties http) {
        if (http == null || http.getServices() == null) {
            return null;
        }
        Map<String, Object> capService = (Map<String, Object>) http.getServices().get("cap");
        if (capService != null) {
            Map<String, Object> credentials = (Map<String, Object>) capService.get("credentials");
            if (credentials != null) {
                Object username = credentials.get("username");
                return username != null ? username.toString() : null;
            }
        }
        return null;
    }

    

    @SuppressWarnings("unchecked")
    public static String getCapPassword(HttpModuleProperties http) {
        if (http == null || http.getServices() == null) {
            return null;
        }
        Map<String, Object> capService = (Map<String, Object>) http.getServices().get("cap");
        if (capService != null) {
            Map<String, Object> credentials = (Map<String, Object>) capService.get("credentials");
            if (credentials != null) {
                Object password = credentials.get("password");
                return password != null ? password.toString() : null;
            }
        }
        return null;
    }
}
