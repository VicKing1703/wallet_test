package com.uplatform.wallet_tests.api.kafka.dto;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletProjectionMessage(
        @JsonProperty("type") String type,
        @JsonProperty("seq_number") long seqNumber,
        @JsonProperty("wallet_uuid") String walletUuid,
        @JsonProperty("player_uuid") String playerUuid,
        @JsonProperty("node_uuid") String nodeUuid,
        @JsonProperty("payload") String payload,
        @JsonProperty("currency") String currency,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("seq_number_node_uuid") String seqNumberNodeUuid
) {}

