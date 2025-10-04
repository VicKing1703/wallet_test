package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameSessionStartMessage(
        MessageDetails message,
        @JsonProperty("player_id") String playerId,
        @JsonProperty("player_bonus_uuid") String playerBonusUuid,
        @JsonProperty("node_id") String nodeId,
        String id,
        String ip,
        @JsonProperty("provider_id") String providerId,
        @JsonProperty("provider_external_id") String providerExternalId,
        @JsonProperty("game_type_name") String gameTypeName,
        @JsonProperty("game_id") String gameId,
        @JsonProperty("game_external_id") String gameExternalId,
        String currency,
        @JsonProperty("start_date") Long startDate,
        @JsonProperty("game_mode") String gameMode,
        String useragent,
        @JsonProperty("wallet_uuid") String walletUuid,
        @JsonProperty("secret_key") String secretKey,
        @JsonProperty("category_id") String categoryId,
        @JsonProperty("type_id") String typeId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageDetails(String eventType) {}
}