package com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // null-поля не сериализуются
public class BrandV1Payload {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("localized_names")
    private Map<LangEnum, String> localizedNames; // не входит в событие delete

    @JsonProperty("alias")
    private String alias; // не входит в событие delete

    @JsonProperty("project_id")
    private String projectId; // не входит в событие delete

    @JsonProperty("status_enabled")
    private Boolean statusEnabled; // не входит в событие delete

    @JsonProperty("created_at")
    private Integer createdAt; // для событий create

    @JsonProperty("updated_at")
    private Integer updatedAt; // для событий update

    @JsonProperty("deleted_at")
    private Integer deletedAt; // для событий delete

}
