package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.uplatform.wallet_tests.api.http.config.HttpServiceCredentials;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletHttpServiceOverrides {
    private HttpServiceCredentials credentials;
    private String secret;
    private String casinoId;
}
