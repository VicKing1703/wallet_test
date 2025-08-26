package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandV3Payload {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("alias")
    private  String alias;

    @JsonProperty("localized_names")
    private Map<LangEnum, String> localizedNames;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("status")
    private String status;
}
