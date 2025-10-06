package com.uplatform.wallet_tests.api.http.cap.dto.update_player_properties;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlayerPropertiesRequest {

    @JsonProperty("isManuallyBlocked")
    private boolean manuallyBlocked;

    @JsonProperty("blockDeposit")
    private boolean blockDeposit;

    @JsonProperty("blockWithdrawal")
    private boolean blockWithdrawal;

    @JsonProperty("blockGambling")
    private boolean blockGambling;

    @JsonProperty("blockBetting")
    private boolean blockBetting;
}
