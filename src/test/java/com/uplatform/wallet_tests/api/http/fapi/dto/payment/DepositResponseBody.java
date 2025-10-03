package com.uplatform.wallet_tests.api.http.fapi.dto.payment;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DepositResponseBody {
    private String uuid;
}
