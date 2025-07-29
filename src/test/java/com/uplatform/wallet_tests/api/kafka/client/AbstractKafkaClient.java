package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import org.opentest4j.AssertionFailedError;
import com.uplatform.wallet_tests.api.kafka.client.KafkaExpectationBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import com.uplatform.wallet_tests.api.kafka.consumer.MessageFinder;

public abstract class AbstractKafkaClient {

    protected final KafkaBackgroundConsumer kafkaBackgroundConsumer;
    protected final Duration defaultFindTimeout;

    protected AbstractKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        this.kafkaBackgroundConsumer = kafkaBackgroundConsumer;
        this.defaultFindTimeout = configProvider.getKafkaConfig().getFindMessageTimeout();
    }

    protected <T> T expectMessage(
            Map<String, String> filter,
            Class<T> messageClass
    ) {
        Duration timeout = this.defaultFindTimeout;
        String typeDescription = messageClass.getSimpleName();
        String searchDetails = buildSearchDetails(filter);

        return kafkaBackgroundConsumer.findMessage(filter, timeout, messageClass)
                .orElseThrow(() -> new AssertionFailedError(
                        String.format(
                                "Kafka message %s %s not found within %s. Filter: %s",
                                typeDescription,
                                searchDetails,
                                timeout,
                                filter
                        ),
                        filter,
                        String.format("Message '%s' not received", typeDescription)
                ));
    }

    protected <T> T expectUniqueMessage(
            Map<String, String> filter,
            Class<T> messageClass
    ) {
        Duration timeout = this.defaultFindTimeout;
        String typeDescription = messageClass.getSimpleName();
        String searchDetails = buildSearchDetails(filter);

        MessageFinder.FindResult<T> result = kafkaBackgroundConsumer.findAndCountMessages(filter, timeout, messageClass);

        result.getFirstMatch().orElseThrow(() -> new AssertionFailedError(
                String.format(
                        "Kafka message %s %s not found within %s. Filter: %s",
                        typeDescription,
                        searchDetails,
                        timeout,
                        filter
                ),
                filter,
                String.format("Message '%s' not received", typeDescription)
        ));

        return verifyUnique(result, filter, messageClass);
    }

    protected <T> T verifyUnique(MessageFinder.FindResult<T> result, Map<String, String> filter, Class<T> messageClass) {
        int count = result.getCount();
        if (count != 1) {
            String typeDescription = messageClass.getSimpleName();
            String searchDetails = buildSearchDetails(filter);
            throw new AssertionFailedError(
                    String.format(
                            "Kafka message %s %s expected once but found %d. Filter: %s",
                            typeDescription,
                            searchDetails,
                            count,
                            filter
                    ),
                    filter,
                    String.format("Message '%s' is not unique", typeDescription)
            );
        }
        return result.getFirstMatch().get();
    }

    protected String buildSearchDetails(Map<String, String> filter) {
        return filter.entrySet().stream()
                .map(e -> e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining(", "));
    }

    public <T> KafkaExpectationBuilder<T> expect(Class<T> messageClass) {
        return new KafkaExpectationBuilder<>(this.kafkaBackgroundConsumer, this.defaultFindTimeout, messageClass);
    }
}
