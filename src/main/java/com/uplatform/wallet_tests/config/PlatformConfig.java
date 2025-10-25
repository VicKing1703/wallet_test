package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformConfig {
    private String currency;
    private String country;
    private String nodeId;
    private String groupId;
}
