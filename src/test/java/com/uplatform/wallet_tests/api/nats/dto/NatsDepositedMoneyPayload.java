package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsDepositStatus;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
public class NatsDepositedMoneyPayload {
    private String uuid;

    @JsonProperty("currency_code")
    private String currencyCode;

    private BigDecimal amount;

    private NatsDepositStatus status;

    @JsonProperty("node_uuid")
    private String nodeUuid;

    @JsonProperty("bonus_id")
    private String bonusId;

    public BigDecimal amount() {
        return amount;
    }
}
