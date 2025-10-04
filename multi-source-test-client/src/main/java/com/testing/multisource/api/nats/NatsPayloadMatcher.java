package com.testing.multisource.api.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NatsPayloadMatcher {

    private final ObjectMapper objectMapper;

    @SuppressWarnings("rawtypes")
    public boolean matches(Object payload, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        if (payload == null) {
            return false;
        }
        Map payloadMap = objectMapper.convertValue(payload, Map.class);
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            try {
                Object actualValue = JsonPath.read(payloadMap, filter.getKey());
                if (!Objects.toString(actualValue, null)
                        .equals(Objects.toString(filter.getValue(), null))) {
                    return false;
                }
            } catch (PathNotFoundException e) {
                return false;
            }
        }
        return true;
    }
}
