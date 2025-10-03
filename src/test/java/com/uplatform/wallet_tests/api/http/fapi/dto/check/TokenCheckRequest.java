package com.uplatform.wallet_tests.api.http.fapi.dto.check;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenCheckRequest {
    private String username;
    private String password;
}