package com.uplatform.wallet_tests.api.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.nats.config.NatsConfigProvider;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.config.NatsConfig;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NatsClient {

    private final NatsSubscriber subscriber;
    private final String streamPrefix;
    private final String natsBaseName;
    private final Duration searchTimeout;
    private final Duration defaultUniqueWindow;

    @Autowired
    public NatsClient(ObjectMapper objectMapper,
                      NatsAttachmentHelper attachmentHelper,
                      NatsPayloadMatcher payloadMatcher,
                      NatsConnectionManager connectionManager,
                      NatsConfigProvider configProvider) {
        NatsConfig natsConfig = configProvider.getNatsConfig();

        this.streamPrefix = configProvider.getNatsStreamPrefix();
        this.natsBaseName = natsConfig.getStreamName();
        String streamName = connectionManager.getStreamName();

        this.searchTimeout = Duration.ofSeconds(natsConfig.getSearchTimeoutSeconds());
        this.defaultUniqueWindow = Duration.ofMillis(natsConfig.getUniqueDuplicateWindowMs());

        this.subscriber = new NatsSubscriber(
                connectionManager.getConnection(),
                connectionManager.getJetStream(),
                objectMapper,
                attachmentHelper,
                payloadMatcher,
                searchTimeout,
                Duration.ofSeconds(natsConfig.getSubscriptionAckWaitSeconds()),
                Duration.ofSeconds(natsConfig.getSubscriptionInactiveThresholdSeconds()),
                streamName,
                natsConfig.getSubscriptionBufferSize(),
                natsConfig.getSubscriptionRetryCount(),
                natsConfig.getSubscriptionRetryDelayMs(),
                natsConfig.isFailOnDeserialization()
        );
    }

    public String buildWalletSubject(String playerUuid, String walletUuid) {
        String subjectBase = this.streamPrefix + "." + this.natsBaseName;
        String wildcard = "*";
        
        return String.format("%s.%s.%s.%s", subjectBase, wildcard, playerUuid, walletUuid);
    }

    public <T> CompletableFuture<NatsMessage<T>> findMessageAsync(String subject,
                                                                  Class<T> messageType,
                                                                  Map<String, Object> jsonPathFilters,
                                                                  Map<String, Object> metadataFilters,
                                                                  BiPredicate<T, String> legacyFilter,
                                                                  boolean legacyFilterUsed,
                                                                  Duration timeout) {
        return subscriber.findMessageAsync(subject, messageType, jsonPathFilters, metadataFilters,
                legacyFilter, legacyFilterUsed, timeout);
    }

    public <T> CompletableFuture<NatsMessage<T>> findUniqueMessageAsync(String subject,
                                                                        Class<T> messageType,
                                                                        Map<String, Object> jsonPathFilters,
                                                                        Map<String, Object> metadataFilters,
                                                                        BiPredicate<T, String> legacyFilter,
                                                                        boolean legacyFilterUsed,
                                                                        Duration duplicateWindow,
                                                                        Duration timeout) {
        return subscriber.findUniqueMessageAsync(subject, messageType, jsonPathFilters, metadataFilters,
                legacyFilter, legacyFilterUsed, duplicateWindow, timeout);
    }

    public <T> CompletableFuture<NatsMessage<T>> findUniqueMessageAsync(String subject,
                                                                        Class<T> messageType,
                                                                        Map<String, Object> jsonPathFilters,
                                                                        Map<String, Object> metadataFilters,
                                                                        BiPredicate<T, String> legacyFilter,
                                                                        boolean legacyFilterUsed,
                                                                        Duration timeout) {
        return findUniqueMessageAsync(subject, messageType, jsonPathFilters, metadataFilters,
                legacyFilter, legacyFilterUsed, defaultUniqueWindow, timeout);
    }

    public <T> NatsExpectationBuilder<T> expect(Class<T> messageType) {
        return new NatsExpectationBuilder<>(this, messageType, this.searchTimeout);
    }

    Duration getSearchTimeout() {
        return this.searchTimeout;
    }

    Duration getDefaultUniqueWindow() {
        return this.defaultUniqueWindow;
    }
}