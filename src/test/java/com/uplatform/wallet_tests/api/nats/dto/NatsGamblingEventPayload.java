package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsMessageName;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsTransactionDirection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NatsGamblingEventPayload(
        String uuid,
        @JsonProperty("bet_uuid") String betUuid,
        @JsonProperty("game_session_uuid") String gameSessionUuid,
        @JsonProperty("provider_round_id") String providerRoundId,
        String currency,
        BigDecimal amount,
        NatsGamblingTransactionType type,
        @JsonProperty("provider_round_closed") boolean providerRoundClosed,
        NatsMessageName message,
        @JsonProperty("created_at") Long createdAt,
        NatsTransactionDirection direction,
        NatsGamblingTransactionOperation operation,
        @JsonProperty("node_uuid") String nodeUuid,
        @JsonProperty("game_uuid") String gameUuid,
        @JsonProperty("provider_uuid") String providerUuid,
        @JsonProperty("wagered_deposit_info") List<Map<String, Object>> wageredDepositInfo,
        @JsonProperty("currency_conversion_info") CurrencyConversionInfo currencyConversionInfo
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrencyConversionInfo(
            @JsonProperty("game_amount") BigDecimal gameAmount,
            @JsonProperty("game_currency") String gameCurrency,
            @JsonProperty("currency_rates") List<CurrencyRate> currencyRates
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrencyRate(
            @JsonProperty("base_currency") String baseCurrency,
            @JsonProperty("quote_currency") String quoteCurrency,
            String value,
            @JsonProperty("updated_at") Long updatedAt
    ) {
    }
}
