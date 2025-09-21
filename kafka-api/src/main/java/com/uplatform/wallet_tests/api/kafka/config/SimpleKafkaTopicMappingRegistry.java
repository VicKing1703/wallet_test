package com.uplatform.wallet_tests.api.kafka.config;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class SimpleKafkaTopicMappingRegistry implements KafkaTopicMappingRegistry {

    private final Map<Class<?>, String> topicMap;

    public SimpleKafkaTopicMappingRegistry(Map<Class<?>, String> topicMap) {
        this.topicMap = Map.copyOf(topicMap);
    }

    @Override
    public Optional<String> getTopicSuffixFor(Class<?> messageType) {
        String suffix = topicMap.get(messageType);
        return Optional.ofNullable(suffix);
    }

    @Override
    public Collection<String> getAllTopicSuffixes() {
        Set<String> suffixes = topicMap.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(suffixes);
    }
}
