package com.uplatform.wallet_tests.tests.util.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

@Slf4j
@Component
public class HttpSignatureUtil {

    private final ObjectMapper objectMapper;
    private final String apiSecret;

    @Autowired
    public HttpSignatureUtil(ObjectMapper objectMapper,
                             @Value("${app.api.manager.secret}") String apiSecret) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.apiSecret = Objects.requireNonNull(apiSecret,
                "API secret cannot be null (check property 'api.signature.secret')");
    }

    public String createSignature(ApiEndpoints endpoint, Object body) {
        return createSignature(endpoint, "", body);
    }

    public String createSignature(ApiEndpoints endpoint, String queryParams, Object body) {
        long timestamp = Instant.now().getEpochSecond();
        String bodyStr  = serializeBody(body);

        String queryStr = (queryParams != null && !queryParams.trim().isEmpty())
                ? "?" + queryParams
                : "";

        String signStr = String.format("%d.%s%s.%s",
                timestamp,
                endpoint.getPath(),
                queryStr,
                bodyStr
        );

        String signature = HmacUtils.hmacSha256Hex(this.apiSecret, signStr);
        return String.format("t=%d,v1=%s", timestamp, signature);
    }

    private String serializeBody(Object body) {
        if (body == null) {
            return "";
        }
        try {
            return this.objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body for signature", e);
        }
    }
}
