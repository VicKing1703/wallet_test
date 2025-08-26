package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.enums.GameEventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameV3Message {

    @JsonProperty("eventType")
    private GameEventType eventType;

}
