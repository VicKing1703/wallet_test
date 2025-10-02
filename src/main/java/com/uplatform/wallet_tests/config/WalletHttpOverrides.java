package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletHttpOverrides {
    private Map<String, WalletHttpServiceOverrides> services = new LinkedHashMap<>();
}
