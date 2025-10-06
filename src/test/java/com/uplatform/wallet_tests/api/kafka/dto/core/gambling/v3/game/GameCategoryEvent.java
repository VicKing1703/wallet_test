package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.payload.CategoryV3Payload;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameCategoryEvent {
    @JsonProperty("message")
    private GameV3Message message;

    @JsonProperty("category")
    private CategoryV3Payload category;
}
