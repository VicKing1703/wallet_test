package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandDeletePayload {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("deleted_at")
    private Long deleted_at;
}
