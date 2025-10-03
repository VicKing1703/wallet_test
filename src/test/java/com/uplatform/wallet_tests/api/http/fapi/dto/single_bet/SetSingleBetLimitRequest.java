package com.uplatform.wallet_tests.api.http.fapi.dto.single_bet;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetSingleBetLimitRequest {
    private String amount;
    private String currency;
}