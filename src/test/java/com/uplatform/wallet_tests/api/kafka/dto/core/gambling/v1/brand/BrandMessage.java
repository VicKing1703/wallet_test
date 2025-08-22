package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.enums.BrandEventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandMessage {

    @JsonProperty("eventType")
    private BrandEventType eventType;

}
