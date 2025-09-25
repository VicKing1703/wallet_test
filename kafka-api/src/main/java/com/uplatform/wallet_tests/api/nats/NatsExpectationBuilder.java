package com.uplatform.wallet_tests.api.nats;

import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsDeserializationException;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsDuplicateMessageException;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsMessageNotFoundException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

public class NatsExpectationBuilder<T> {
    private final NatsClient client;
    private final Class<T> messageType;
    private final Duration defaultTimeout;
    private String subject;
    private final Map<String, Object> jsonPathFilters = new LinkedHashMap<>();
    private final Map<String, Object> metadataFilters = new LinkedHashMap<>();
    private BiPredicate<T, String> legacyFilter = (p, t) -> true;
    private boolean legacyFilterUsed = false;
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

    @Deprecated
    public NatsExpectationBuilder<T> with(BiPredicate<T, String> filter) {
        if (filter == null) {
            this.legacyFilter = (p, t) -> true;
            this.legacyFilterUsed = false;
        } else {
            this.legacyFilter = filter;
            this.legacyFilterUsed = true;
        }
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

    public CompletableFuture<NatsMessage<T>> fetchAsync() {
        if (subject == null) {
            throw new IllegalStateException("Subject must be specified");
        }
        Duration effectiveTimeout = this.timeout != null ? this.timeout : defaultTimeout;
        Map<String, Object> payloadFilters = Map.copyOf(this.jsonPathFilters);
        Map<String, Object> metaFilters = Map.copyOf(this.metadataFilters);
        Duration searchTimeout = effectiveTimeout.isNegative()
                ? defaultTimeout
                : effectiveTimeout.truncatedTo(ChronoUnit.MILLIS);

        if (unique) {
            Duration window = this.duplicateWindow != null ? this.duplicateWindow : client.getDefaultUniqueWindow();
            return client.findUniqueMessageAsync(
                    subject,
                    messageType,
                    payloadFilters,
                    metaFilters,
                    legacyFilter,
                    legacyFilterUsed,
                    window,
                    searchTimeout
            );
        }

        return client.findMessageAsync(
                subject,
                messageType,
                payloadFilters,
                metaFilters,
                legacyFilter,
                legacyFilterUsed,
                searchTimeout
        );
    }

    public NatsMessage<T> fetch() {
        try {
            return fetchAsync().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NatsMessageNotFoundException("Fetching NATS message interrupted", e);
        } catch (Exception e) {
            Throwable cause = e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
            if (cause instanceof NatsDuplicateMessageException) {
                throw (NatsDuplicateMessageException) cause;
            } else if (cause instanceof NatsDeserializationException) {
                throw (NatsDeserializationException) cause;
            } else {
                throw new NatsMessageNotFoundException("Failed to fetch NATS message", cause);
            }
        }
    }
}
