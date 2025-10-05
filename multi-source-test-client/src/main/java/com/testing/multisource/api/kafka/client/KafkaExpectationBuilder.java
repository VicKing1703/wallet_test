package com.testing.multisource.api.kafka.client;

import com.testing.multisource.api.kafka.consumer.KafkaBackgroundConsumer;
import com.testing.multisource.api.kafka.consumer.MessageFinder;
import com.testing.multisource.api.kafka.exceptions.KafkaMessageNotFoundException;
import com.testing.multisource.api.kafka.exceptions.KafkaMessageNotUniqueException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class KafkaExpectationBuilder<T> {
    private final KafkaBackgroundConsumer consumer;
    private final Duration defaultTimeout;
    private final Duration defaultUniqueWindow;
    private final Class<T> messageType;
    private final Map<String, String> filters = new HashMap<>();
    private boolean unique = false;
    private Duration timeout;
    private Duration duplicateWindow;

    public KafkaExpectationBuilder(KafkaBackgroundConsumer consumer, Duration defaultTimeout, Duration defaultUniqueWindow, Class<T> messageType) {
        this.consumer = consumer;
        this.defaultTimeout = defaultTimeout;
        this.defaultUniqueWindow = defaultUniqueWindow;
        this.messageType = messageType;
    }

    public KafkaExpectationBuilder<T> with(String key, Object value) {
        if (value != null) {
            this.filters.put(key, String.valueOf(value));
        }
        return this;
    }

    public KafkaExpectationBuilder<T> unique() {
        this.unique = true;
        this.duplicateWindow = defaultUniqueWindow;
        return this;
    }

    public KafkaExpectationBuilder<T> unique(Duration window) {
        this.unique = true;
        this.duplicateWindow = window;
        return this;
    }

    public KafkaExpectationBuilder<T> within(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public T fetch() {
        Duration effectiveTimeout = this.timeout != null ? this.timeout : defaultTimeout;
        String typeDescription = messageType.getSimpleName();
        String searchDetails = buildSearchDetails(filters);

        if (unique) {
            Duration window = this.duplicateWindow != null ? this.duplicateWindow : defaultUniqueWindow;
            MessageFinder.FindResult<T> result = consumer.findAndCountMessagesWithinWindow(
                    filters, effectiveTimeout, messageType, window.toMillis());
            result.getFirstMatch().orElseThrow(() -> new KafkaMessageNotFoundException(
                    String.format("Kafka message %s %s not found within %s. Filter: %s",
                            typeDescription, searchDetails, effectiveTimeout, filters)));

            int count = result.getCount();
            if (count != 1) {
                throw new KafkaMessageNotUniqueException(
                        String.format("Kafka message %s %s expected once but found %d within %dms window. Filter: %s",
                                typeDescription, searchDetails, count, window.toMillis(), filters));
            }
            return result.getFirstMatch().get();
        } else {
            return consumer.findMessage(filters, effectiveTimeout, messageType)
                    .orElseThrow(() -> new KafkaMessageNotFoundException(
                            String.format("Kafka message %s %s not found within %s. Filter: %s",
                                    typeDescription, searchDetails, effectiveTimeout, filters)));
        }
    }

    private String buildSearchDetails(Map<String, String> filter) {
        return filter.entrySet().stream()
                .map(e -> e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining(", "));
    }
}

