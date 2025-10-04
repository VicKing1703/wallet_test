package com.testing.multisource.api.kafka.consumer;

import com.testing.multisource.api.kafka.config.KafkaConfigProvider;
import com.testing.multisource.api.kafka.config.KafkaTopicMappingRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MessageBuffer {

    private final int bufferSize;
    private final String topicPrefix;
    private final KafkaTopicMappingRegistry topicMappingRegistry;

    private final ConcurrentHashMap<String, LinkedBlockingDeque<ConsumerRecord<String, String>>> buffers = new ConcurrentHashMap<>();
    private List<String> fullListeningTopics;

    public MessageBuffer(
            KafkaConfigProvider configProvider,
            KafkaTopicMappingRegistry topicMappingRegistry
    ) {
        this.bufferSize = configProvider.getKafkaConfig().bufferSize();
        this.topicPrefix = configProvider.getTopicPrefix();
        this.topicMappingRegistry = topicMappingRegistry;
    }

    @PostConstruct
    public void initialize() {
        Collection<String> topicSuffixes = topicMappingRegistry.getAllTopicSuffixes();
        this.fullListeningTopics = topicSuffixes.stream()
                .filter(suffix -> suffix != null && !suffix.isBlank())
                .map(suffix -> topicPrefix + suffix)
                .distinct()
                .collect(Collectors.toUnmodifiableList());
        int capacity = bufferSize > 0 ? bufferSize : Integer.MAX_VALUE;
        this.fullListeningTopics.forEach(topic -> buffers.put(topic, new LinkedBlockingDeque<>(capacity)));
    }

    public void addRecord(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        LinkedBlockingDeque<ConsumerRecord<String, String>> buffer = buffers.get(topic);

        if (buffer != null) {
            if (bufferSize > 0 && buffer.remainingCapacity() == 0) {
                ConsumerRecord<String, String> removed = buffer.pollFirst();
                if (removed != null) {
                    log.warn("Buffer overflow: Removed oldest message [Topic: {}, Offset: {}]. Buffer size now: {}",
                            topic,
                            removed.offset(),
                            buffer.size());
                }
            }
            buffer.offerLast(record);
        } else {
            log.error("Received message for unexpected/unconfigured topic buffer: {}. Message ignored. Ensure this topic is registered in KafkaTopicMappingRegistry. Listening to: {}",
                    topic,
                    fullListeningTopics);
        }
    }

    public Deque<ConsumerRecord<String, String>> getBufferForTopic(String topicName) {
        return buffers.get(topicName);
    }

    public boolean isTopicConfigured(String topicName) {
        return buffers.containsKey(topicName);
    }

    public List<String> getConfiguredTopics() {
        return this.fullListeningTopics;
    }

    public void clearAllBuffers() {
        buffers.values().forEach(LinkedBlockingDeque::clear);
        log.info("All message buffers cleared.");
    }

    public void clearBuffer(String topicName) {
        LinkedBlockingDeque<ConsumerRecord<String, String>> buffer = buffers.get(topicName);
        if (buffer != null) {
            buffer.clear();
        } else {
            log.warn("Attempted to clear buffer for unconfigured topic: {}", topicName);
        }
    }
}
