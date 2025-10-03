package com.uplatform.wallet_tests.api.http.fapi.dto.payment;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestBody {
    private String amount;
    private PaymentMethodId paymentMethodId;
    private String currency;
    private String country;
    private Map<String, Object> context;
    private RedirectUrls redirect;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedirectUrls {
        private String failed;
        private String success;
        private String pending;
    }
}
