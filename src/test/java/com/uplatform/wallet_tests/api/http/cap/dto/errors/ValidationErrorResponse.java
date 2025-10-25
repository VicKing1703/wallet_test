package com.uplatform.wallet_tests.api.http.cap.dto.errors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.uplatform.wallet_tests.tests.util.utils.FlexibleErrorMapDeserializer;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ValidationErrorResponse(
        int code,
        String message,
        @JsonDeserialize(using = FlexibleErrorMapDeserializer.class)
        Map<String, List<String>> errors
) {
}
