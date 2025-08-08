package com.uplatform.wallet_tests.api.redis.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.config.RedisAggregateConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.time.Duration;
import org.awaitility.core.ConditionTimeoutException;

import static org.awaitility.Awaitility.await;

import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.uplatform.wallet_tests.api.attachment.AttachmentType;
import com.uplatform.wallet_tests.api.redis.client.CheckResult;

@Slf4j
@Component
public class RedisRetryHelper {

    private final ObjectMapper objectMapper;
    private final AllureAttachmentService attachmentService;
    private final int retryAttempts;
    private final long retryDelayMs;

    public RedisRetryHelper(ObjectMapper objectMapper,
                           AllureAttachmentService attachmentService,
                           EnvironmentConfigurationProvider configProvider) {
        this.objectMapper = objectMapper;
        this.attachmentService = attachmentService;

        RedisAggregateConfig aggregateConfig = Optional.ofNullable(configProvider)
                .map(EnvironmentConfigurationProvider::getEnvironmentConfig)
                .map(com.uplatform.wallet_tests.config.EnvironmentConfig::getRedis)
                .map(com.uplatform.wallet_tests.config.RedisConfig::getAggregate)
                .orElseThrow(() -> new IllegalStateException(
                        "RedisAggregateConfig not found in EnvironmentConfigurationProvider. Cannot initialize RedisRetryHelper."));

        this.retryAttempts = aggregateConfig.getRetryAttempts();
        this.retryDelayMs = aggregateConfig.getRetryDelayMs();

        log.info("RedisRetryHelper initialized with {} attempts and {}ms delay", retryAttempts, retryDelayMs);
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public long getTotalTimeoutMs() {
        return (long) retryAttempts * retryDelayMs;
    }

    public <T> T deserializeValue(String rawValue, JavaType javaType) throws JsonProcessingException {
        return objectMapper.readValue(rawValue, javaType);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> waitForValue(
            String instance,
            String key,
            Type valueType,
            Object valueTypeInfo,
            BiFunction<String, String, Optional<String>> valueGetter,
            BiFunction<T, String, CheckResult> checkFunc,
            boolean attachOnSuccess) {

        String typeName = (valueTypeInfo instanceof Class<?> ? ((Class<?>) valueTypeInfo).getSimpleName() : valueTypeInfo.toString())
                .replace("com.fasterxml.jackson.core.type.TypeReference<", "")
                .replace(">", "");

        JavaType javaType = objectMapper.constructType(valueType);

        Optional<String> rawValueOpt = awaitValue(key, () -> valueGetter.apply(instance, key));

        if (rawValueOpt.isEmpty()) {
            String errorMsg = "Key not found or timeout while waiting";
            log.error("Failed to find expected value for key '{}' in Redis instance [{}]: {}", key, instance, errorMsg);
            attachmentService.attachText(AttachmentType.REDIS,
                    "Final State (Failure)",
                    createAttachmentContent(instance, key, null, null, errorMsg));
            return Optional.empty();
        }

        String rawValue = rawValueOpt.get();
        try {
            T deserializedValue = deserializeValue(rawValue, javaType);

            if (checkFunc != null) {
                CheckResult checkResult = runCheck(checkFunc, deserializedValue, rawValue);
                if (!checkResult.isSuccess()) {
                    String errorMsg = "Check failed: " + checkResult.getMessage();
                    log.error("[{}] {}", instance, errorMsg);
                    attachmentService.attachText(AttachmentType.REDIS,
                            "Final State (Validation Failed)",
                            createAttachmentContent(instance, key, deserializedValue, rawValue, errorMsg));
                    return Optional.empty();
                }
                if (attachOnSuccess) {
                    attachValue("Redis Value Found", instance, key, deserializedValue, rawValue,
                            checkResult.getMessage());
                }
            } else if (attachOnSuccess) {
                attachValue("Redis Value Found", instance, key, deserializedValue, rawValue,
                        "Check not required");
            }

            log.info("Successfully found value for key '{}' in Redis instance [{}]", key, instance);
            return Optional.of(deserializedValue);

        } catch (JsonProcessingException e) {
            String errorMsg = "Failed to deserialize JSON: " + e.getMessage();
            log.error("[{}] Failed to deserialize JSON to type {}. Error: {}", instance, typeName, e.getMessage());
            attachmentService.attachText(AttachmentType.REDIS,
                    "Final State (Deserialization Failure)",
                    createAttachmentContent(instance, key, null, rawValue, errorMsg));
            return Optional.empty();
        } catch (Exception e) {
            String errorMsg = "Unexpected error during value processing: " + e.getMessage();
            log.error("[{}] Unexpected error processing key '{}'. Error: {}", instance, key, e.getMessage(), e);
            attachmentService.attachText(AttachmentType.REDIS,
                    "Final State (Error)",
                    createAttachmentContent(instance, key, null, rawValue, errorMsg));
            return Optional.empty();
        }
    }

    public <T> Optional<T> awaitValue(String key, Supplier<Optional<T>> supplier) {
        try {
            return await()
                    .alias("Redis await key: " + key)
                    .atMost(Duration.ofMillis(getTotalTimeoutMs()))
                    .pollInterval(Duration.ofMillis(retryDelayMs))
                    .pollDelay(Duration.ZERO)
                    .ignoreExceptions()
                    .until(() -> supplier.get(), Optional::isPresent);
        } catch (ConditionTimeoutException e) {
            return Optional.empty();
        }
    }

    private <T> CheckResult runCheck(BiFunction<T, String, CheckResult> checkFunc, T value, String rawValue) {
        return checkFunc.apply(value, rawValue);
    }

    private <T> void attachValue(String title, String instance, String key, T value, String rawValue, String status) {
        attachmentService.attachText(AttachmentType.REDIS, title,
                createAttachmentContent(instance, key, value, rawValue, status));
    }

    public <T> String createAttachmentContent(String instance, String key, T deserializedValue, String rawValue, String statusMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Redis Instance: ").append(instance).append("\n");
        sb.append("Key: ").append(key).append("\n");
        sb.append("Status: ").append(statusMessage).append("\n\n");

        if (deserializedValue != null) {
            sb.append("Deserialized Type: ").append(deserializedValue.getClass().getName()).append("\n");
            try {
                sb.append("Deserialized Value (JSON):\n");
                sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserializedValue));
            } catch (JsonProcessingException e) {
                sb.append("Could not format deserialized value as pretty JSON: ").append(e.getMessage()).append("\n");
                sb.append("Deserialized Value (toString()):\n").append(deserializedValue);
            }
        } else if (rawValue != null && !rawValue.isEmpty()) {
            sb.append("Raw Value:\n").append(rawValue);
        } else {
            sb.append("No value retrieved or value was empty.");
        }
        return sb.toString();
    }
}
