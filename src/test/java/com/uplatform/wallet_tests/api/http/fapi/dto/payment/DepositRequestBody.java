package com.uplatform.wallet_tests.api.http.fapi.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequestBody {
    private String amount;
    private PaymentMethodId paymentMethodId;
    private String currency;
    private String country;
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
