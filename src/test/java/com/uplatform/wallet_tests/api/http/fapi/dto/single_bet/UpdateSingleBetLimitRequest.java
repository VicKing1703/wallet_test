package com.uplatform.wallet_tests.api.http.fapi.dto.single_bet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSingleBetLimitRequest {
    private String amount;
}
