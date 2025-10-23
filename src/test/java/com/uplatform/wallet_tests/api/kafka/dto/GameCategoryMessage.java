package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GameCategoryMessage(
        MessageEnvelope message,
        Category category
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageEnvelope(
            @JsonProperty("eventType") String eventType
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            String uuid,
            String name,
            @JsonProperty("localized_names") Map<String, String> localizedNames,
            String type,
            String status
    ) {
    }
}
