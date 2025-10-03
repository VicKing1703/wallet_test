package com.uplatform.wallet_tests.api.http.fapi.dto.identity;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRequest {
    private String number;
    private String type;
    private String issuedDate;
    private String expiryDate;
}
