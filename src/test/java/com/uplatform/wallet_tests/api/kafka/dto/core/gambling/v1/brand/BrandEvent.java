package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrandEvent {

    @JsonProperty("message")
    private BrandMessage message;

    @JsonProperty("brand")
    private BrandV1Payload brand;
}
