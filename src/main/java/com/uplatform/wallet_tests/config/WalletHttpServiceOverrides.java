package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletHttpServiceOverrides {
    private String secret;
    private String casinoId;
    private Credentials credentials;
    private String platformUserId;
    private String platformUsername;
}
