package com.uplatform.wallet_tests.api.http.fapi.dto.deposit;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DepositLimit {

    @JsonProperty("id")
    private String id;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private boolean status;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("spent")
    private BigDecimal spent;

    @JsonProperty("rest")
    private BigDecimal rest;

    @JsonProperty("type")
    private String type;

    @JsonProperty("startedAt")
    private Long startedAt;

    @JsonProperty("expiresAt")
    private Long expiresAt;

    @JsonProperty("deactivatedAt")
    private Long deactivatedAt;

    @JsonProperty("upcomingChanges")
    private List<UpcomingChange> upcomingChanges;

    @JsonProperty("required")
    private boolean required;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpcomingChange {
        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("effectiveAt")
        private Long effectiveAt;
    }
}
