package com.uplatform.wallet_tests.api.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.nats.config.NatsConfigProvider;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsDeserializationException;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsDuplicateMessageException;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsMessageNotFoundException;
import com.uplatform.wallet_tests.config.modules.nats.NatsConfig;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
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
        this.natsBaseName = natsConfig.streamName();
        String streamName = connectionManager.getStreamName();

        this.searchTimeout = Duration.ofSeconds(natsConfig.searchTimeoutSeconds());
        this.defaultUniqueWindow = Duration.ofMillis(natsConfig.uniqueDuplicateWindowMs());

        this.subscriber = new NatsSubscriber(
                connectionManager.getConnection(),
                connectionManager.getJetStream(),
                objectMapper,
                attachmentHelper,
                payloadMatcher,
                searchTimeout,
                Duration.ofSeconds(natsConfig.subscriptionAckWaitSeconds()),
                Duration.ofSeconds(natsConfig.subscriptionInactiveThresholdSeconds()),
                streamName,
                natsConfig.subscriptionBufferSize(),
                natsConfig.subscriptionRetryCount(),
                natsConfig.subscriptionRetryDelayMs(),
                natsConfig.failOnDeserialization()
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
                                                                  Duration timeout) {
        return subscriber.findMessageAsync(subject, messageType, jsonPathFilters, metadataFilters, timeout);
    }

    public <T> NatsMessage<T> findMessage(String subject,
                                          Class<T> messageType,
                                          Map<String, Object> jsonPathFilters,
                                          Map<String, Object> metadataFilters,
                                          Duration timeout) {
        CompletableFuture<NatsMessage<T>> future = findMessageAsync(subject, messageType, jsonPathFilters,
                metadataFilters, timeout);
        return resolveFuture(subject, messageType, timeout, future);
    }

    public <T> CompletableFuture<NatsMessage<T>> findUniqueMessageAsync(String subject,
                                                                        Class<T> messageType,
                                                                        Map<String, Object> jsonPathFilters,
                                                                        Map<String, Object> metadataFilters,
                                                                        Duration duplicateWindow,
                                                                        Duration timeout) {
        return subscriber.findUniqueMessageAsync(subject, messageType, jsonPathFilters, metadataFilters,
                duplicateWindow, timeout);
    }

    public <T> CompletableFuture<NatsMessage<T>> findUniqueMessageAsync(String subject,
                                                                        Class<T> messageType,
                                                                        Map<String, Object> jsonPathFilters,
                                                                        Map<String, Object> metadataFilters,
                                                                        Duration timeout) {
        return findUniqueMessageAsync(subject, messageType, jsonPathFilters, metadataFilters,
                defaultUniqueWindow, timeout);
    }

    public <T> NatsMessage<T> findUniqueMessage(String subject,
                                                Class<T> messageType,
                                                Map<String, Object> jsonPathFilters,
                                                Map<String, Object> metadataFilters,
                                                Duration duplicateWindow,
                                                Duration timeout) {
        CompletableFuture<NatsMessage<T>> future = findUniqueMessageAsync(subject, messageType, jsonPathFilters,
                metadataFilters, duplicateWindow, timeout);
        return resolveFuture(subject, messageType, timeout, future);
    }

    public <T> NatsMessage<T> findUniqueMessage(String subject,
                                                Class<T> messageType,
                                                Map<String, Object> jsonPathFilters,
                                                Map<String, Object> metadataFilters,
                                                Duration timeout) {
        return findUniqueMessage(subject, messageType, jsonPathFilters, metadataFilters, defaultUniqueWindow, timeout);
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

    private <T> NatsMessage<T> resolveFuture(String subject,
                                             Class<T> messageType,
                                             Duration timeout,
                                             CompletableFuture<NatsMessage<T>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NatsMessageNotFoundException(
                    String.format("Interrupted while waiting for NATS message %s on subject '%s'",
                            messageType.getSimpleName(), subject), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NatsDuplicateMessageException duplicate) {
                throw duplicate;
            }
            if (cause instanceof NatsDeserializationException deserialization) {
                throw deserialization;
            }
            if (cause instanceof NatsMessageNotFoundException notFound) {
                throw notFound;
            }
            if (cause instanceof TimeoutException timeoutException) {
                throw new NatsMessageNotFoundException(
                        String.format("Timed out after %s waiting for NATS message %s on subject '%s'",
                                timeout != null ? timeout : this.searchTimeout,
                                messageType.getSimpleName(),
                                subject),
                        timeoutException);
            }
            throw new NatsMessageNotFoundException(
                    String.format("Failed while waiting for NATS message %s on subject '%s'",
                            messageType.getSimpleName(), subject), cause);
        }
    }
}
