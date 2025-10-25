package com.uplatform.wallet_tests.api.http.fapi.dto.get_games;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FapiGame(
        @JsonProperty("id") String id,
        @JsonProperty("alias") String alias,
        @JsonProperty("name") String name,
        @JsonProperty("image") String image,
        @JsonProperty("providerName") String providerName,
        @JsonProperty("ruleResource") String ruleResource,
        @JsonProperty("hasDemo") boolean hasDemo,
        @JsonProperty("canPlayDemo") boolean canPlayDemo,
        @JsonProperty("brand") Brand brand
) {
}