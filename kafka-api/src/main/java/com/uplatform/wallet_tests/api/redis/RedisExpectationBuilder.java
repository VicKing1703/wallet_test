package com.uplatform.wallet_tests.api.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.uplatform.wallet_tests.api.attachment.AttachmentType;
import com.uplatform.wallet_tests.api.redis.config.RedisAwaitilityProperties;
import com.uplatform.wallet_tests.api.redis.exceptions.RedisRetryExhaustedException;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;

public class RedisExpectationBuilder<T> {

    private final String beanName;
    private final String instanceName;
    private final String key;
    private final RedisTemplate<String, String> redisTemplate;
    private final TypeReference<T> typeReference;
    private final ObjectMapper objectMapper;
    private final AllureAttachmentService attachmentService;
    private final RedisAwaitilityProperties awaitilityProperties;

    private final List<FieldExpectation> expectations = new ArrayList<>();
    private Duration customTimeout;

    RedisExpectationBuilder(String beanName,
                            String instanceName,
                            String key,
                            RedisTemplate<String, String> redisTemplate,
                            TypeReference<T> typeReference,
                            ObjectMapper objectMapper,
                            AllureAttachmentService attachmentService,
                            RedisAwaitilityProperties awaitilityProperties) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Redis key must not be null or blank");
        }
        this.beanName = beanName;
        this.instanceName = instanceName;
        this.key = key;
        this.redisTemplate = redisTemplate;
        this.typeReference = typeReference;
        this.objectMapper = objectMapper;
        this.attachmentService = attachmentService;
        this.awaitilityProperties = awaitilityProperties;
    }

    public RedisExpectationBuilder<T> with(String jsonPath, Object expectedValue) {
        String normalizedPath = normalize(jsonPath);
        String description = "== " + formatValue(expectedValue);
        Predicate<Object> predicate = actual -> equalsConsideringNumbers(actual, expectedValue);
        expectations.add(new FieldExpectation(normalizedPath, predicate, description));
        return this;
    }

    public RedisExpectationBuilder<T> with(String jsonPath, Predicate<Object> predicate) {
        return with(jsonPath, predicate, "matches predicate");
    }

    public RedisExpectationBuilder<T> with(String jsonPath, Predicate<Object> predicate, String description) {
        expectations.add(new FieldExpectation(normalize(jsonPath), predicate, description));
        return this;
    }

    public RedisExpectationBuilder<T> withAtLeast(String jsonPath, Number threshold) {
        Objects.requireNonNull(threshold, "threshold");
        String description = ">= " + threshold;
        Predicate<Object> predicate = actual -> {
            if (!(actual instanceof Number actualNumber)) {
                return false;
            }
            return toBigDecimal(actualNumber).compareTo(toBigDecimal(threshold)) >= 0;
        };
        expectations.add(new FieldExpectation(normalize(jsonPath), predicate, description));
        return this;
    }

    public RedisExpectationBuilder<T> within(Duration timeout) {
        this.customTimeout = timeout;
        return this;
    }

    public T fetch() {
        Duration timeout = Optional.ofNullable(customTimeout).orElse(awaitilityProperties.defaultTimeout());
        Duration pollInterval = awaitilityProperties.pollInterval();

        attachSearchInfo(timeout);

        AtomicReference<AttemptResult<T>> lastAttempt = new AtomicReference<>();
        Instant start = Instant.now();
        try {
            AttemptResult<T> result = await()
                    .alias("Redis fetch: " + key)
                    .pollDelay(Duration.ZERO)
                    .pollInterval(pollInterval)
                    .atMost(timeout)
                    .ignoreExceptionsMatching(ex -> !(ex instanceof RedisDeserializationRuntimeException))
                    .until(() -> {
                        AttemptResult<T> attempt = attemptFetch();
                        lastAttempt.set(attempt);
                        return attempt;
                    }, AttemptResult::found);

            attachSuccess(result.rawJson(), result.value());
            return result.value();
        } catch (RedisDeserializationRuntimeException ex) {
            attachDeserializationError(ex.rawJson(), ex.getCause());
            throw new RedisRetryExhaustedException(ex.getMessage(), ex.getCause());
        } catch (ConditionTimeoutException ex) {
            AttemptResult<T> attempt = lastAttempt.get();
            String reason = attempt != null ? attempt.failureMessage() : "value not found";
            Duration elapsed = Duration.between(start, Instant.now());
            attachNotFound(reason, elapsed);
            throw new RedisRetryExhaustedException(String.format(
                    "[%s] Value for key '%s' was not found within %d seconds: %s",
                    instanceName,
                    key,
                    timeout.toSeconds(),
                    reason
            ));
        }
    }

    private AttemptResult<T> attemptFetch() {
        String rawValue = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(rawValue)) {
            return AttemptResult.notFound("value is null or empty");
        }

        DocumentContext documentContext;
        try {
            documentContext = JsonPath.parse(rawValue);
        } catch (Exception e) {
            return AttemptResult.notFound("failed to parse JSON: " + e.getMessage());
        }

        for (FieldExpectation expectation : expectations) {
            Object actual;
            try {
                actual = documentContext.read(expectation.jsonPath());
            } catch (PathNotFoundException ex) {
                return AttemptResult.notFound("json path '" + expectation.jsonPath() + "' not found");
            } catch (Exception ex) {
                return AttemptResult.notFound("failed to evaluate json path '" + expectation.jsonPath() + "': " + ex.getMessage());
            }
            if (!expectation.predicate().test(actual)) {
                return AttemptResult.notFound("json path '" + expectation.jsonPath() + "' " + expectation.description()
                        + " but was " + formatValue(actual));
            }
        }

        try {
            JavaType javaType = objectMapper.constructType(typeReference.getType());
            T value = objectMapper.readValue(rawValue, javaType);
            return AttemptResult.found(value, rawValue);
        } catch (JsonProcessingException e) {
            throw new RedisDeserializationRuntimeException(key, rawValue, e);
        }
    }

    private void attachSearchInfo(Duration timeout) {
        StringBuilder builder = new StringBuilder();
        builder.append("Client Bean: ").append(beanName).append('\n');
        builder.append("Redis Instance: ").append(instanceName).append('\n');
        builder.append("Key: ").append(key).append('\n');
        builder.append("Timeout: ").append(timeout.toSeconds()).append("s\n");
        if (expectations.isEmpty()) {
            builder.append("Filters: [none]");
        } else {
            builder.append("Filters:");
            for (FieldExpectation expectation : expectations) {
                builder.append('\n')
                        .append(" - ")
                        .append(expectation.jsonPath())
                        .append(" ")
                        .append(expectation.description());
            }
        }
        attachmentService.attachText(AttachmentType.REDIS, "Search Info", builder.toString());
    }

    private void attachSuccess(String rawJson, T value) {
        StringBuilder builder = new StringBuilder();
        builder.append("Key: ").append(key).append('\n');
        builder.append("Status: Найдено\n\n");
        builder.append(prettyJson(rawJson, value));
        attachmentService.attachText(AttachmentType.REDIS, "Found Value", builder.toString());
    }

    private void attachNotFound(String reason, Duration elapsed) {
        StringBuilder builder = new StringBuilder();
        builder.append("Key: ").append(key).append('\n');
        builder.append("Status: Не найдено за ").append(elapsed.toSeconds()).append(" секунд\n");
        builder.append("Последняя причина: ").append(reason);
        attachmentService.attachText(AttachmentType.REDIS, "Value Not Found", builder.toString());
    }

    private void attachDeserializationError(String rawJson, Throwable error) {
        StringBuilder builder = new StringBuilder();
        builder.append("Key: ").append(key).append('\n');
        builder.append("Status: Ошибка десериализации\n");
        builder.append("Ошибка: ").append(error != null ? error.getMessage() : "unknown").append("\n\n");
        builder.append("Raw JSON:\n").append(rawJson);
        attachmentService.attachText(AttachmentType.REDIS, "Deserialization Error", builder.toString());
    }

    private String prettyJson(String rawJson, T deserialized) {
        try {
            if (deserialized != null) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserialized);
            }
            return objectMapper.readTree(rawJson).toPrettyString();
        } catch (JsonProcessingException e) {
            return rawJson;
        }
    }

    private String normalize(String jsonPath) {
        if (!StringUtils.hasText(jsonPath)) {
            throw new IllegalArgumentException("jsonPath must not be blank");
        }
        String trimmed = jsonPath.trim();
        if (trimmed.startsWith("$")) {
            return trimmed;
        }
        if (trimmed.startsWith(".")) {
            return "$" + trimmed;
        }
        return "$." + trimmed;
    }

    private boolean equalsConsideringNumbers(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return Objects.equals(actual, expected);
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return toBigDecimal(actualNumber).compareTo(toBigDecimal(expectedNumber)) == 0;
        }
        return Objects.equals(actual, expected);
    }

    private java.math.BigDecimal toBigDecimal(Number number) {
        if (number instanceof java.math.BigDecimal bd) {
            return bd;
        }
        return new java.math.BigDecimal(number.toString());
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String str) {
            return '"' + str + '"';
        }
        return value.toString();
    }

    private record FieldExpectation(String jsonPath, Predicate<Object> predicate, String description) {
    }

    private record AttemptResult<T>(T value, String rawJson, String failureMessage) {
        static <T> AttemptResult<T> found(T value, String rawJson) {
            return new AttemptResult<>(value, rawJson, null);
        }

        static <T> AttemptResult<T> notFound(String message) {
            return new AttemptResult<>(null, null, message);
        }

        boolean found() {
            return value != null;
        }
    }

    private static class RedisDeserializationRuntimeException extends RuntimeException {
        private final String rawJson;

        RedisDeserializationRuntimeException(String key, String rawJson, Throwable cause) {
            super(String.format("Failed to deserialize redis key '%s': %s", key, cause.getMessage()), cause);
            this.rawJson = rawJson;
        }

        String rawJson() {
            return rawJson;
        }
    }
}

