package com.uplatform.wallet_tests.api.http.fapi.dto.turnover;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTurnoverLimitRequest {
    private String amount;
}
