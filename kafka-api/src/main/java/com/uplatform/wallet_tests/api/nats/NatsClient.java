package com.uplatform.wallet_tests.api.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.nats.config.NatsConfigProvider;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.config.NatsConfig;

import java.time.Duration;
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
                                                                  BiPredicate<T, String> filter) {
        return subscriber.findMessageAsync(subject, messageType, filter);
    }

    public <T> CompletableFuture<NatsMessage<T>> findUniqueMessageAsync(String subject,
                                                                        Class<T> messageType,
                                                                        BiPredicate<T, String> filter,
                                                                        Duration duplicateWindow) {
        return subscriber.findUniqueMessageAsync(subject, messageType, filter, duplicateWindow);
    }

    public <T> CompletableFuture<NatsMessage<T>> findUniqueMessageAsync(String subject,
                                                                        Class<T> messageType,
                                                                        BiPredicate<T, String> filter) {
        return findUniqueMessageAsync(subject, messageType, filter, defaultUniqueWindow);
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