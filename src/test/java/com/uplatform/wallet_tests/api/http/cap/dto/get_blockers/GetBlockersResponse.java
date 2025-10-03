package com.uplatform.wallet_tests.api.http.cap.dto.get_blockers;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetBlockersResponse {
    private boolean gamblingEnabled;
    private boolean bettingEnabled;
}