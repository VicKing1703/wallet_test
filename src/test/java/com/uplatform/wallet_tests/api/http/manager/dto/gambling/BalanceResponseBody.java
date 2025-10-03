package com.uplatform.wallet_tests.api.http.manager.dto.gambling;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponseBody {
    private BigDecimal balance;
}