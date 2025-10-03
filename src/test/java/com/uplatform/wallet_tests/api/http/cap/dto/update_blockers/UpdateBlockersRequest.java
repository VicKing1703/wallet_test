package com.uplatform.wallet_tests.api.http.cap.dto.update_blockers;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBlockersRequest {
    private Boolean gamblingEnabled;
    private Boolean bettingEnabled;
}