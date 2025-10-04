package com.testing.multisource.api.kafka.config;

import java.util.Collection;
import java.util.Optional;

public interface KafkaTopicMappingRegistry {
    Optional<String> getTopicSuffixFor(Class<?> messageType);
    Collection<String> getAllTopicSuffixes();
}
