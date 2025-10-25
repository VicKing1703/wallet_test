package com.uplatform.wallet_tests.api.http.fapi.dto.get_games;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetGamesResponseBody(
        @JsonProperty("total") int total,
        @JsonProperty("games") List<FapiGame> games
) {
}