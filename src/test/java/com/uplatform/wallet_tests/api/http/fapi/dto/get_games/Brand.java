package com.uplatform.wallet_tests.api.http.fapi.dto.get_games;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Brand(
        @JsonProperty("id") String id,
        @JsonProperty("alias") String alias,
        @JsonProperty("name") String name,
        @JsonProperty("icon") String icon,
        @JsonProperty("logo") String logo,
        @JsonProperty("colorLogo") String colorLogo
) {
}