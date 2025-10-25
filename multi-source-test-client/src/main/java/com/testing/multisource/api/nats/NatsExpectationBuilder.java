package com.testing.multisource.api.nats;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.api.nats.exceptions.NatsDuplicateMessageException;
import com.testing.multisource.api.nats.exceptions.NatsMessageNotFoundException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NatsExpectationBuilder<T> {
    private final NatsClient client;
    private final Class<T> messageType;
    private final Duration defaultTimeout;
    private String subject;
    private final Map<String, Object> jsonPathFilters = new LinkedHashMap<>();
    private final Map<String, Object> metadataFilters = new LinkedHashMap<>();
    private boolean unique = false;
    private Duration timeout;
    private Duration duplicateWindow;

    public NatsExpectationBuilder(NatsClient client, Class<T> messageType, Duration defaultTimeout) {
        this.client = client;
        this.messageType = messageType;
        this.defaultTimeout = defaultTimeout;
    }

    public NatsExpectationBuilder<T> from(String subject) {
        this.subject = subject;
        return this;
    }

    public NatsExpectationBuilder<T> with(String jsonPath, Object expectedValue) {
        if (jsonPath != null && expectedValue != null) {
            this.jsonPathFilters.put(jsonPath, expectedValue);
        }
        return this;
    }

    public NatsExpectationBuilder<T> withType(String expectedType) {
        if (expectedType != null) {
            this.metadataFilters.put("type", expectedType);
        }
        return this;
    }

    public NatsExpectationBuilder<T> withSequence(long expectedSequence) {
        this.metadataFilters.put("sequence", expectedSequence);
        return this;
    }

    public NatsExpectationBuilder<T> unique() {
        this.unique = true;
        this.duplicateWindow = client.getDefaultUniqueWindow();
        return this;
    }

    public NatsExpectationBuilder<T> unique(Duration window) {
        this.unique = true;
        this.duplicateWindow = window;
        return this;
    }

    public NatsExpectationBuilder<T> within(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public NatsMessage<T> fetch() {
        if (subject == null) {
            throw new IllegalStateException("Subject must be specified");
        }

        Duration candidateTimeout = this.timeout != null ? this.timeout : defaultTimeout;
        Duration effectiveTimeout = candidateTimeout.isNegative()
                ? defaultTimeout
                : candidateTimeout.truncatedTo(ChronoUnit.MILLIS);

        Map<String, Object> payloadFilters = Map.copyOf(this.jsonPathFilters);
        Map<String, Object> metaFilters = Map.copyOf(this.metadataFilters);

        String typeDescription = messageType.getSimpleName();
        String searchDetails = buildSearchDetails(payloadFilters, metaFilters);

        try {
            if (unique) {
                Duration window = this.duplicateWindow != null ? this.duplicateWindow : client.getDefaultUniqueWindow();
                return client.findUniqueMessage(subject, messageType, payloadFilters, metaFilters, window, effectiveTimeout);
            }

            return client.findMessage(subject, messageType, payloadFilters, metaFilters, effectiveTimeout);
        } catch (NatsMessageNotFoundException e) {
            throw new NatsMessageNotFoundException(
                    String.format("NATS message %s %s not found on subject '%s' within %s.",
                            typeDescription,
                            searchDetails,
                            subject,
                            effectiveTimeout),
                    e);
        } catch (NatsDuplicateMessageException e) {
            throw new NatsDuplicateMessageException(
                    String.format("NATS message %s %s expected once on subject '%s' but duplicates detected.",
                            typeDescription,
                            searchDetails,
                            subject),
                    e);
        }
    }

    private String buildSearchDetails(Map<String, Object> payloadFilters, Map<String, Object> metadataFilters) {
        List<String> parts = metadataFilters.entrySet().stream()
                .map(entry -> String.format("meta[%s] = %s", entry.getKey(), String.valueOf(entry.getValue())))
                .collect(Collectors.toList());

        parts.addAll(payloadFilters.entrySet().stream()
                .map(entry -> String.format("jsonPath[%s] = %s", entry.getKey(), String.valueOf(entry.getValue())))
                .collect(Collectors.toList()));

        if (parts.isEmpty()) {
            return "with no filters";
        }

        return "with filters: " + String.join(", ", parts);
    }
}
