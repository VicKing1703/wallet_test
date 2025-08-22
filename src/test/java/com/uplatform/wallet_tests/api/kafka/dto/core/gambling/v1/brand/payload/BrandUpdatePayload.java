package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandUpdatePayload {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("localized_names")
    private Map<LangEnum, String> localized_names;

    @JsonProperty("alias")
    private String alias;

    @JsonProperty("project_id")
    private String project_id;

    @JsonProperty("status_enabled")
    private Boolean status_enabled;

    @JsonProperty("updated_at")
    private Long updated_at;
}
