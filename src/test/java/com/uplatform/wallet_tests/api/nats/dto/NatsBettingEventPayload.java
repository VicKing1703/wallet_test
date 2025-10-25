package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NatsBettingEventPayload(
        String uuid,
        NatsBettingTransactionOperation type,
        @JsonProperty("bet_id") long betId,
        @JsonProperty("bet_info") List<BetInfoDetail> betInfo,
        BigDecimal amount,
        @JsonProperty("raw_amount") BigDecimal rawAmount,
        @JsonProperty("total_coeff") BigDecimal totalCoeff,
        long time,
        @JsonProperty("created_at") Long createdAt,
        @JsonProperty("wagered_deposit_info") List<Object> wageredDepositInfo
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BetInfoDetail(
            @JsonProperty("ChampName") String champName,
            @JsonProperty("Coef") BigDecimal coef,
            @JsonProperty("CouponType") String couponType,
            @JsonProperty("Event") String event,
            @JsonProperty("GameName") String gameName,
            @JsonProperty("Score") String score,
            @JsonProperty("SportName") String sportName,
            @JsonProperty("ChampId") Long champId,
            @JsonProperty("DateStart") Long dateStart
    ) {
    }
}
