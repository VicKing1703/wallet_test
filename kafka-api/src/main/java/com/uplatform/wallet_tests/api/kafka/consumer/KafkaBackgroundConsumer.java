package com.uplatform.wallet_tests.api.kafka.consumer;

import com.uplatform.wallet_tests.api.attachment.AttachmentService;
import com.uplatform.wallet_tests.api.attachment.AttachmentType;
import com.uplatform.wallet_tests.api.kafka.config.KafkaConfigProvider;
import com.uplatform.wallet_tests.api.kafka.config.KafkaTopicMappingRegistry;
import com.uplatform.wallet_tests.api.kafka.consumer.MessageFinder.FindResult;
import com.uplatform.wallet_tests.api.kafka.exceptions.KafkaDeserializationException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.awaitility.Awaitility.await;

@Component
@Slf4j
public class KafkaBackgroundConsumer {

    private final KafkaTopicMappingRegistry topicMappingRegistry;
    private final KafkaPollingService pollingService;
    private final MessageBuffer messageBuffer;
    private final MessageFinder messageFinder;
    private final KafkaAllureReporter allureReporter;
    private final AttachmentService attachmentService;
    private final String topicPrefix;
    private final Duration findMessageSleepInterval;

    public KafkaBackgroundConsumer(
            KafkaTopicMappingRegistry topicMappingRegistry,
            KafkaPollingService pollingService,
            MessageBuffer messageBuffer,
            MessageFinder messageFinder,
            KafkaAllureReporter allureReporter,
            AttachmentService attachmentService,
            KafkaConfigProvider configProvider
    ) {
        this.topicMappingRegistry = topicMappingRegistry;
        this.pollingService = pollingService;
        this.messageBuffer = messageBuffer;
        this.messageFinder = messageFinder;
        this.allureReporter = allureReporter;
        this.topicPrefix = configProvider.getTopicPrefix();
        this.findMessageSleepInterval = configProvider.getKafkaConfig().findMessageSleepInterval();
        this.attachmentService = attachmentService;
    }

    @PostConstruct
    public void initializeAndStart() {
        pollingService.start();
    }

    @PreDestroy
    public void shutdown() {
        if (pollingService != null) {
            pollingService.stop();
        }
    }

    public <T> Optional<T> findMessage(
            Map<String, String> filterCriteria,
            Duration timeout,
            Class<T> targetClass
    ) {
        TopicValidationResult validationResult = validateAndGetTopicName(targetClass);
        if (!validationResult.isValid()) {
            handleValidationFailure(validationResult, targetClass, filterCriteria);
            return Optional.empty();
        }

        String fullTopicName = validationResult.fullTopicName();
        allureReporter.addSearchInfoAttachment(fullTopicName, "(inferred from Type)", targetClass, filterCriteria);

        Callable<Optional<T>> searchCallable = () -> {
            Deque<ConsumerRecord<String, String>> buffer = messageBuffer.getBufferForTopic(fullTopicName);
            return messageFinder.searchAndDeserialize(buffer, filterCriteria, targetClass, fullTopicName);
        };

        try {
            Optional<T> foundMessage = await()
                    .alias("search for message in " + fullTopicName)
                    .atMost(timeout)
                    .pollInterval(findMessageSleepInterval)
                    .until(searchCallable, Optional::isPresent);
            return foundMessage;
        } catch (ConditionTimeoutException e) {
            log.warn("Timeout after {} waiting for message. Topic: '{}', Target Type: '{}', Criteria: {}",
                    timeout, fullTopicName, targetClass.getSimpleName(), filterCriteria);
            allureReporter.addMessagesNotFoundAttachment(fullTopicName, filterCriteria, targetClass, "(inferred from Type)");
            return Optional.empty();
        } catch (KafkaDeserializationException kde) {
            throw kde;
        } catch (Exception ex) {
            log.error("Unexpected error during findMessage: {}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    public <T> FindResult<T> findAndCountMessages(
            Map<String, String> filterCriteria,
            Duration timeout,
            Class<T> targetClass
    ) {
        TopicValidationResult validationResult = validateAndGetTopicName(targetClass);
        if (!validationResult.isValid()) {
            handleValidationFailure(validationResult, targetClass, filterCriteria);
            return new FindResult<>(Optional.empty(), List.of(), 0);
        }

        String fullTopicName = validationResult.fullTopicName();
        allureReporter.addSearchInfoAttachment(fullTopicName, "(inferred from Type)", targetClass, filterCriteria);

        Callable<FindResult<T>> searchCallable = () -> {
            Deque<ConsumerRecord<String, String>> buffer = messageBuffer.getBufferForTopic(fullTopicName);
            return messageFinder.findAndCount(buffer, filterCriteria, targetClass, fullTopicName);
        };

        try {
            FindResult<T> result = await()
                    .alias("search for message in " + fullTopicName)
                    .atMost(timeout)
                    .pollInterval(findMessageSleepInterval)
                    .until(searchCallable, r -> r.getFirstMatch().isPresent());
            return result;
        } catch (ConditionTimeoutException e) {
            log.warn("Timeout after {} waiting for message. Topic: '{}', Target Type: '{}', Criteria: {}",
                    timeout, fullTopicName, targetClass.getSimpleName(), filterCriteria);
            allureReporter.addMessagesNotFoundAttachment(fullTopicName, filterCriteria, targetClass, "(inferred from Type)");
            try {
                // After hitting the timeout we run the search once more to capture the final buffer snapshot for attachments.
                return searchCallable.call();
            } catch (Exception ex) {
                log.warn("Error evaluating final result after timeout: {}", ex.getMessage());
                return new FindResult<>(Optional.empty(), List.of(), 0);
            }
        } catch (KafkaDeserializationException kde) {
            throw kde;
        } catch (Exception ex) {
            log.error("Unexpected error during findAndCountMessages: {}", ex.getMessage(), ex);
            return new FindResult<>(Optional.empty(), List.of(), 0);
        }
    }

    public <T> int countMessages(
            Map<String, String> filterCriteria,
            Class<T> targetClass
    ) {
        Optional<String> topicSuffixOpt = topicMappingRegistry.getTopicSuffixFor(targetClass);
        if (topicSuffixOpt.isEmpty()) {
            log.error("Cannot count messages: No topic suffix configured for class {}.", targetClass.getName());
            return 0;
        }

        String topicSuffix = topicSuffixOpt.get();
        String fullTopicName = topicPrefix + topicSuffix;

        if (!messageBuffer.isTopicConfigured(fullTopicName)) {
            log.error("Topic '{}' (for type {}) is not configured to be listened to. Configured topics: {}. Ensure the type is registered in KafkaTopicMappingRegistry.",
                    fullTopicName, targetClass.getName(), messageBuffer.getConfiguredTopics());
            return 0;
        }

        Deque<ConsumerRecord<String, String>> buffer = messageBuffer.getBufferForTopic(fullTopicName);
        return messageFinder.countMatchingMessages(buffer, filterCriteria);
    }

    public void clearAllMessageBuffers() {
        if (messageBuffer != null) {
            messageBuffer.clearAllBuffers();
        }
    }

    public void clearMessageBufferForTopic(String topicSuffix) {
        if (messageBuffer != null) {
            String fullTopicName = topicPrefix + topicSuffix;
            messageBuffer.clearBuffer(fullTopicName);
        }
    }

    private TopicValidationResult validateAndGetTopicName(Class<?> targetClass) {
        Optional<String> topicSuffixOpt = topicMappingRegistry.getTopicSuffixFor(targetClass);
        if (topicSuffixOpt.isEmpty()) {
            return TopicValidationResult.missingMapping();
        }

        String topicSuffix = topicSuffixOpt.get();
        String fullTopicName = topicPrefix + topicSuffix;

        if (!messageBuffer.isTopicConfigured(fullTopicName)) {
            return TopicValidationResult.topicNotConfigured(fullTopicName);
        }

        return TopicValidationResult.valid(fullTopicName);
    }

    private <T> void handleValidationFailure(
            TopicValidationResult validationResult,
            Class<T> targetClass,
            Map<String, String> filterCriteria
    ) {
        if (validationResult.error() == TopicValidationError.MISSING_MAPPING) {
            log.error("Cannot find message: No topic suffix configured for class {}.", targetClass.getName());
            attachmentService.attachText(
                    AttachmentType.KAFKA,
                    "Search Error - No Topic Mapping",
                    String.format("No topic suffix mapping for %s.", targetClass.getSimpleName()));
        } else if (validationResult.error() == TopicValidationError.TOPIC_NOT_LISTENED) {
            String fullTopicName = validationResult.fullTopicName();
            log.error("Topic '{}' (for type {}) is not configured to be listened to. Configured topics: {}. Ensure the type is registered in KafkaTopicMappingRegistry.",
                    fullTopicName, targetClass.getName(), messageBuffer.getConfiguredTopics());
            allureReporter.addSearchInfoAttachment(fullTopicName, "(inferred from Type, but not listened)", targetClass, filterCriteria);
            attachmentService.attachText(
                    AttachmentType.KAFKA,
                    "Search Error - Topic Not Listened",
                    String.format("Topic '%s' (for %s) is not in the list of listened topics. Listened topics: %s. Ensure the mapping is present in KafkaTopicMappingRegistry.",
                            fullTopicName, targetClass.getSimpleName(), messageBuffer.getConfiguredTopics()));
        }
    }

    private record TopicValidationResult(String fullTopicName, TopicValidationError error) {
        private static TopicValidationResult missingMapping() {
            return new TopicValidationResult(null, TopicValidationError.MISSING_MAPPING);
        }

        private static TopicValidationResult topicNotConfigured(String fullTopicName) {
            return new TopicValidationResult(fullTopicName, TopicValidationError.TOPIC_NOT_LISTENED);
        }

        private static TopicValidationResult valid(String fullTopicName) {
            return new TopicValidationResult(fullTopicName, TopicValidationError.NONE);
        }

        private boolean isValid() {
            return error == TopicValidationError.NONE;
        }
    }

    private enum TopicValidationError {
        NONE,
        MISSING_MAPPING,
        TOPIC_NOT_LISTENED
    }
}
