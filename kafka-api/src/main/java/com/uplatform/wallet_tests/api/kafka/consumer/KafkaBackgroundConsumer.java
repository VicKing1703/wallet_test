package com.uplatform.wallet_tests.api.kafka.consumer;

import com.uplatform.wallet_tests.api.kafka.config.KafkaTopicMappingRegistry;
import com.uplatform.wallet_tests.api.kafka.config.KafkaConfigProvider;
import com.uplatform.wallet_tests.api.attachment.AttachmentService;
import com.uplatform.wallet_tests.api.attachment.AttachmentType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Deque;
import com.uplatform.wallet_tests.api.kafka.consumer.MessageFinder.FindResult;
import java.util.List;
import java.util.concurrent.Callable;
import static org.awaitility.Awaitility.await;
import org.awaitility.core.ConditionTimeoutException;
import com.uplatform.wallet_tests.api.kafka.exceptions.KafkaDeserializationException;

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
        pollingService.start(messageBuffer.getConfiguredTopics());
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
        Optional<String> topicSuffixOpt = topicMappingRegistry.getTopicSuffixFor(targetClass);
        if (topicSuffixOpt.isEmpty()) {
            log.error("Cannot find message: No topic suffix configured for class {}.", targetClass.getName());
            attachmentService.attachText(
                    AttachmentType.KAFKA,
                    "Search Error - No Topic Mapping",
                    String.format("No topic suffix mapping for %s.", targetClass.getSimpleName()));
            return Optional.empty();
        }

        String topicSuffix = topicSuffixOpt.get();
        String fullTopicName = topicPrefix + topicSuffix;

        if (!messageBuffer.isTopicConfigured(fullTopicName)) {
            log.error("Topic '{}' (for type {}) is not configured to be listened to. Configured topics: {}.",
                    fullTopicName, targetClass.getName(), messageBuffer.getConfiguredTopics());
            allureReporter.addSearchInfoAttachment(fullTopicName, "(inferred from Type, but not listened)", targetClass, filterCriteria);
            attachmentService.attachText(
                    AttachmentType.KAFKA,
                    "Search Error - Topic Not Listened",
                    String.format("Topic '%s' (for %s) is not in the list of listened topics. Listened topics: %s",
                            fullTopicName, targetClass.getSimpleName(), messageBuffer.getConfiguredTopics()));
            return Optional.empty();
        }

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
        }
    }

    public <T> FindResult<T> findAndCountMessages(
            Map<String, String> filterCriteria,
            Duration timeout,
            Class<T> targetClass
    ) {
        Optional<String> topicSuffixOpt = topicMappingRegistry.getTopicSuffixFor(targetClass);
        if (topicSuffixOpt.isEmpty()) {
            log.error("Cannot find message: No topic suffix configured for class {}.", targetClass.getName());
            attachmentService.attachText(
                    AttachmentType.KAFKA,
                    "Search Error - No Topic Mapping",
                    String.format("No topic suffix mapping for %s.", targetClass.getSimpleName()));
            return new FindResult<>(Optional.empty(), List.of(), 0);
        }

        String topicSuffix = topicSuffixOpt.get();
        String fullTopicName = topicPrefix + topicSuffix;

        if (!messageBuffer.isTopicConfigured(fullTopicName)) {
            log.error("Topic '{}' (for type {}) is not configured to be listened to. Configured topics: {}.",
                    fullTopicName, targetClass.getName(), messageBuffer.getConfiguredTopics());
            allureReporter.addSearchInfoAttachment(fullTopicName, "(inferred from Type, but not listened)", targetClass, filterCriteria);
            attachmentService.attachText(
                    AttachmentType.KAFKA,
                    "Search Error - Topic Not Listened",
                    String.format("Topic '%s' (for %s) is not in the list of listened topics. Listened topics: %s",
                            fullTopicName, targetClass.getSimpleName(), messageBuffer.getConfiguredTopics()));
            return new FindResult<>(Optional.empty(), List.of(), 0);
        }

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
            log.error("Topic '{}' (for type {}) is not configured to be listened to. Configured topics: {}.",
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
}
