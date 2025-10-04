package com.uplatform.wallet_tests.api.http.fapi.dto.launch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LaunchGameResponseBody(@JsonProperty("url") String url) {
}