package com.testing.multisource.api.nats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testing.multisource.api.nats.dto.NatsMessage;
import io.nats.client.api.AckPolicy;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.Subscription;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.ReplayPolicy;
import io.nats.client.impl.NatsJetStreamMetaData;
import com.testing.multisource.api.nats.exceptions.NatsDeserializationException;
import com.testing.multisource.api.nats.exceptions.NatsDuplicateMessageException;
import com.testing.multisource.api.nats.exceptions.NatsMessageNotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
class NatsSubscriber {
    private final io.nats.client.Connection nc;
    private final JetStream js;
    private final ObjectMapper objectMapper;
    private final NatsAttachmentHelper attachmentHelper;
    private final NatsPayloadMatcher payloadMatcher;
    private final Duration searchTimeout;
    private final Duration ackWaitTimeout;
    private final Duration inactiveThreshold;
    private final String streamName;
    private final int subscriptionBufferSize;
    private final int subscriptionRetryCount;
    private final long subscriptionRetryDelayMs;
    private final boolean failOnDeserialization;

    private interface MatchHandlingStrategy<T> {
        void onFirstMatch(NatsMessage<T> message);
        void onDuplicateMatch(NatsMessage<T> message);
        boolean isCompleted();
    }

    NatsSubscriber(io.nats.client.Connection nc,
                   JetStream js,
                   ObjectMapper objectMapper,
                   NatsAttachmentHelper attachmentHelper,
                   NatsPayloadMatcher payloadMatcher,
                   Duration searchTimeout,
                   Duration ackWaitTimeout,
                   Duration inactiveThreshold,
                   String streamName,
                   int subscriptionBufferSize,
                   int subscriptionRetryCount,
                   long subscriptionRetryDelayMs,
                   boolean failOnDeserialization) {
        this.nc = nc;
        this.js = js;
        this.objectMapper = objectMapper;
        this.attachmentHelper = attachmentHelper;
        this.payloadMatcher = payloadMatcher;
        this.searchTimeout = searchTimeout;
        this.ackWaitTimeout = ackWaitTimeout;
        this.inactiveThreshold = inactiveThreshold;
        this.streamName = streamName;
        this.subscriptionBufferSize = subscriptionBufferSize;
        this.subscriptionRetryCount = subscriptionRetryCount;
        this.subscriptionRetryDelayMs = subscriptionRetryDelayMs;
        this.failOnDeserialization = failOnDeserialization;
    }

    <T> CompletableFuture<NatsMessage<T>> findMessageAsync(String subject,
                                                           Class<T> messageType,
                                                           Map<String, Object> jsonPathFilters,
                                                           Map<String, Object> metadataFilters,
                                                           Duration timeout) {
        CompletableFuture<NatsMessage<T>> future = new CompletableFuture<>();
        Duration effectiveTimeout = timeout != null ? timeout : this.searchTimeout;
        String logPrefix = String.format("NATS SEARCH ASYNC [%s -> %s]", this.streamName, subject);

        attachmentHelper.addSearchInfo(subject, messageType, effectiveTimeout,
                jsonPathFilters, metadataFilters, false, null);

        subscribeWithRetries(subject, future, logPrefix,
                () -> startSubscription(subject, messageType, jsonPathFilters, metadataFilters,
                        future, logPrefix, effectiveTimeout));

        return future;
    }

    <T> CompletableFuture<NatsMessage<T>> findUniqueMessageAsync(String subject,
                                                                 Class<T> messageType,
                                                                 Map<String, Object> jsonPathFilters,
                                                                 Map<String, Object> metadataFilters,
                                                                 Duration duplicateWindow,
                                                                 Duration timeout) {
        CompletableFuture<NatsMessage<T>> future = new CompletableFuture<>();
        Duration effectiveTimeout = timeout != null ? timeout : this.searchTimeout;
        String logPrefix = String.format("NATS SEARCH UNIQUE [%s -> %s]", this.streamName, subject);

        attachmentHelper.addSearchInfo(subject, messageType, effectiveTimeout,
                jsonPathFilters, metadataFilters, true, duplicateWindow);

        subscribeWithRetries(subject, future, logPrefix,
                () -> startUniqueSubscription(subject, messageType, jsonPathFilters, metadataFilters,
                        future, logPrefix, duplicateWindow, effectiveTimeout));

        return future;
    }

    private <T> Dispatcher startSubscription(String subject,
                                             Class<T> messageType,
                                             Map<String, Object> jsonPathFilters,
                                             Map<String, Object> metadataFilters,
                                             CompletableFuture<NatsMessage<T>> future,
                                             String logPrefix,
                                             Duration timeout) throws IOException, JetStreamApiException {
        Dispatcher dispatcher = nc.createDispatcher();
        JavaType javaType = objectMapper.getTypeFactory().constructType(messageType);
        final Dispatcher dispatcherRef = dispatcher;
        final AtomicReference<Subscription> subHolder = new AtomicReference<>();
        final AtomicBoolean firstMatch = new AtomicBoolean(false);

        MatchHandlingStrategy<T> strategy = new MatchHandlingStrategy<>() {
            private final AtomicBoolean completed = new AtomicBoolean(false);
            @Override public void onFirstMatch(NatsMessage<T> message) {
                attachmentHelper.addNatsAttachment("NATS Message Found", message);
                future.complete(message);
                unsubscribeAndClose(dispatcherRef, subHolder.get(), logPrefix + " after match");
                completed.set(true);
            }
            @Override public void onDuplicateMatch(NatsMessage<T> message) {
            }
            @Override public boolean isCompleted() { return completed.get(); }
        };

        MessageHandler handler = msg ->
                processIncomingMessage(msg, javaType, jsonPathFilters, metadataFilters,
                        firstMatch, strategy, logPrefix, future);

        subHolder.set(createSubscription(subject, dispatcherRef, handler));

        awaitMessageFuture(future, dispatcherRef, subHolder.get(), logPrefix, timeout);
        return dispatcher;
    }

    private <T> Dispatcher startUniqueSubscription(String subject,
                                                   Class<T> messageType,
                                                   Map<String, Object> jsonPathFilters,
                                                   Map<String, Object> metadataFilters,
                                                   CompletableFuture<NatsMessage<T>> future,
                                                   String logPrefix,
                                                   Duration duplicateWindow,
                                                   Duration timeout) throws IOException, JetStreamApiException {
        Dispatcher dispatcher = nc.createDispatcher();
        JavaType javaType = objectMapper.getTypeFactory().constructType(messageType);
        final Dispatcher dispatcherRef = dispatcher;
        final AtomicReference<Subscription> subHolder = new AtomicReference<>();
        final AtomicReference<NatsMessage<T>> resultRef = new AtomicReference<>();
        final AtomicBoolean duplicateFound = new AtomicBoolean(false);
        final AtomicBoolean firstMatch = new AtomicBoolean(false);
        final long startTime = System.currentTimeMillis();
        final AtomicLong firstMatchElapsed = new AtomicLong(-1L);
        Duration effectiveTimeout = timeout != null ? timeout : searchTimeout;
        Duration window = (duplicateWindow == null || duplicateWindow.compareTo(effectiveTimeout) > 0)
                ? effectiveTimeout : duplicateWindow;
        final long windowMs = window.toMillis();

        MatchHandlingStrategy<T> strategy = new MatchHandlingStrategy<>() {
            @Override public void onFirstMatch(NatsMessage<T> message) {
                attachmentHelper.addNatsAttachment("NATS Message Found", message);
                resultRef.set(message);
                firstMatchElapsed.set(System.currentTimeMillis() - startTime);
            }
            @Override public void onDuplicateMatch(NatsMessage<T> message) {
                duplicateFound.set(true);
                attachmentHelper.addNatsAttachment("NATS Duplicate Message", message);
                future.completeExceptionally(new NatsDuplicateMessageException("More than one message matched filter"));
                unsubscribeAndClose(dispatcherRef, subHolder.get(), logPrefix + " after duplicate");
                log.info("{} | subject={} firstMatchElapsedMs={} windowMs={} duplicate=true", logPrefix, subject, firstMatchElapsed.get(), windowMs);
            }
            @Override public boolean isCompleted() { return future.isDone(); }
        };

        MessageHandler handler = msg ->
                processIncomingMessage(msg, javaType, jsonPathFilters, metadataFilters,
                        firstMatch, strategy, logPrefix, future);

        subHolder.set(createSubscription(subject, dispatcherRef, handler));

        awaitUniqueMessageFuture(resultRef, duplicateFound, future, dispatcherRef, subHolder.get(),
                logPrefix, window, firstMatchElapsed, subject);
        return dispatcher;
    }

    private <T> void subscribeWithRetries(String subject,
                                          CompletableFuture<NatsMessage<T>> future,
                                          String logPrefix,
                                          Callable<Dispatcher> subscriptionLogic) {
        for (int attempt = 1; attempt <= this.subscriptionRetryCount; attempt++) {
            Dispatcher dispatcher = null;
            try {
                dispatcher = subscriptionLogic.call();
                return;
            } catch (Exception e) {
                if (dispatcher != null) {
                    try {
                        nc.closeDispatcher(dispatcher);
                    } catch (Exception closeEx) {
                        log.warn("{} | Failed to close dispatcher after error: {}", logPrefix, closeEx.getMessage());
                    }
                }

                log.warn("{} | Attempt {}/{} to create NATS subscription failed: {}",
                        logPrefix, attempt, this.subscriptionRetryCount, e.getMessage());

                if (attempt == this.subscriptionRetryCount) {
                    log.error("{} | All {} subscription attempts failed for subject '{}'. Giving up.",
                            logPrefix, this.subscriptionRetryCount, subject, e);
                    future.completeExceptionally(
                            new NatsMessageNotFoundException(
                                    "NATS Subscription failed for " + subject +
                                            " after " + this.subscriptionRetryCount + " attempts", e));
                    return;
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(this.subscriptionRetryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("{} | Subscription retry delay was interrupted.", logPrefix, ie);
                    future.completeExceptionally(
                            new NatsMessageNotFoundException("Subscription retry interrupted for " + subject, ie));
                    return;
                }
            }
        }

        future.completeExceptionally(
                new NatsMessageNotFoundException("Exited subscription retry loop unexpectedly for " + subject));
    }

    private Subscription createSubscription(String subject, Dispatcher dispatcher, MessageHandler handler)
            throws IOException, JetStreamApiException {
        PushSubscribeOptions pso = PushSubscribeOptions.builder()
                .stream(this.streamName)
                .configuration(
                        ConsumerConfiguration.builder()
                                .ackPolicy(AckPolicy.Explicit)
                                .ackWait(ackWaitTimeout)
                                .maxAckPending(subscriptionBufferSize)
                                .inactiveThreshold(inactiveThreshold)
                                .deliverPolicy(DeliverPolicy.All)
                                .replayPolicy(ReplayPolicy.Instant)
                                .build()
                ).build();
        return js.subscribe(subject, dispatcher, handler, false, pso);
    }

    private <T> void processIncomingMessage(Message msg,
                                            JavaType javaType,
                                            Map<String, Object> jsonPathFilters,
                                            Map<String, Object> metadataFilters,
                                            AtomicBoolean firstMatch,
                                            MatchHandlingStrategy<T> strategy,
                                            String logPrefix,
                                            CompletableFuture<NatsMessage<T>> future) {
        long msgSeq = -1L;
        String msgType = null;
        OffsetDateTime timestamp = null;

        try {
            if (msg.isJetStream()) {
                NatsJetStreamMetaData meta = msg.metaData();
                if (meta != null) {
                    msgSeq = meta.streamSequence();
                    ZonedDateTime ts = meta.timestamp();
                    if (ts != null) {
                        timestamp = ts.toOffsetDateTime();
                    }
                } else {
                    log.warn("{} | Received JetStream message without metadata object!", logPrefix);
                }
            } else {
                log.warn("{} | Received non-JetStream message", logPrefix);
                return;
            }

            msgType = msg.getHeaders() != null ? msg.getHeaders().getFirst("type") : null;

            T payload;
            try {
                payload = objectMapper.readValue(msg.getData(), javaType);
            } catch (JsonProcessingException e) {
                if (failOnDeserialization) {
                    log.warn("{} | Failed JSON unmarshal seq={}: {}. Nacking msg.", logPrefix, msgSeq, e.getMessage());
                    safeNack(msg);
                    future.completeExceptionally(new NatsDeserializationException("Failed to deserialize NATS message", e));
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("{} | JSON unmarshal failed seq={} → TERM/ACK and continue", logPrefix, msgSeq);
                    }
                    safeTermOrAck(msg);
                }
                return;
            }

            NatsMessage<T> result = buildNatsMessage(msg, payload, msgSeq, msgType, timestamp);

            if (!matchesAll(result, jsonPathFilters, metadataFilters)) {
                safeTermOrAck(msg);
                if (log.isDebugEnabled()) {
                    log.debug("{} | Non-match terminated: seq={}, subj={}, type={}", logPrefix, msgSeq, msg.getSubject(), msgType);
                }
                return;
            }

            safeAck(msg);

            if (!strategy.isCompleted()) {
                if (firstMatch.compareAndSet(false, true)) {
                    strategy.onFirstMatch(result);
                } else {
                    strategy.onDuplicateMatch(result);
                }
            }
        } catch (Exception e) {
            log.error("{} | Error processing NATS msg (seq≈{}, type≈{}): {}", logPrefix, msgSeq, msgType, e.getMessage(), e);
            safeNack(msg);
            future.completeExceptionally(new NatsDeserializationException("Unexpected error processing NATS message", e));
        }
    }

    private <T> void awaitMessageFuture(CompletableFuture<NatsMessage<T>> future,
                                        Dispatcher dispatcher,
                                        Subscription subscription,
                                        String logPrefix,
                                        Duration timeout) {
        Duration effectiveTimeout = timeout != null ? timeout : searchTimeout;
        future.orTimeout(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS).whenComplete((result, ex) -> {
            String completionLogPrefix = logPrefix + " | Future completed";
            if (ex instanceof TimeoutException) {
                log.warn("{} with Timeout after {}", completionLogPrefix, effectiveTimeout);
            }
            else if (ex != null) {
                log.error("{} with Exception: {}", completionLogPrefix, ex.getMessage(), ex);
            }
            unsubscribeAndClose(dispatcher, subscription, logPrefix + " on completion");
        });
    }

    private <T> void awaitUniqueMessageFuture(AtomicReference<NatsMessage<T>> resultRef,
                                              AtomicBoolean duplicateFound,
                                              CompletableFuture<NatsMessage<T>> future,
                                              Dispatcher dispatcher,
                                              Subscription subscription,
                                              String logPrefix,
                                              Duration window,
                                              AtomicLong firstMatchElapsed,
                                              String subject) {
        Duration effectiveWindow = (window == null || window.isNegative()) ? searchTimeout : window;
        CompletableFuture.delayedExecutor(effectiveWindow.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
            try {
                if (future.isDone()) return;
                boolean duplicate = duplicateFound.get();
                log.info("{} | subject={} firstMatchElapsedMs={} windowMs={} duplicate={}",
                        logPrefix, subject, firstMatchElapsed.get(), effectiveWindow.toMillis(), duplicate);
                if (duplicate) {
                    future.completeExceptionally(new NatsDuplicateMessageException("More than one message matched filter"));
                } else {
                    NatsMessage<T> result = resultRef.get();
                    if (result != null) {
                        future.complete(result);
                    } else {
                        future.completeExceptionally(new NatsMessageNotFoundException("No matching message found"));
                    }
                }
            } finally {
                unsubscribeAndClose(dispatcher, subscription, logPrefix + " on completion");
            }
        });
    }

    private <T> NatsMessage<T> buildNatsMessage(Message msg,
                                                T payload,
                                                long sequence,
                                                String type,
                                                OffsetDateTime timestamp) {
        return NatsMessage.<T>builder()
                .payload(payload)
                .subject(msg.getSubject())
                .type(type)
                .sequence(sequence)
                .timestamp(timestamp)
                .build();
    }

    private <T> boolean matchesAll(NatsMessage<T> message,
                                   Map<String, Object> jsonPathFilters,
                                   Map<String, Object> metadataFilters) {
        return matchesMetadata(message, metadataFilters)
                && payloadMatcher.matches(message.getPayload(), jsonPathFilters);
    }

    private boolean matchesMetadata(NatsMessage<?> message, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object expected = entry.getValue();
            if ("type".equals(key)) {
                if (!Objects.equals(message.getType(), Objects.toString(expected, null))) {
                    return false;
                }
            } else if ("sequence".equals(key)) {
                long expectedSeq;
                if (expected instanceof Number number) {
                    expectedSeq = number.longValue();
                } else {
                    try {
                        expectedSeq = Long.parseLong(Objects.toString(expected, null));
                    } catch (NumberFormatException ex) {
                        return false;
                    }
                }
                if (message.getSequence() != expectedSeq) {
                    return false;
                }
            }
        }
        return true;
    }

    private void safeAck(Message msg) {
        try {
            if (msg.isJetStream()) msg.ack();
        } catch (Exception e) {
            logOnError("ACK", msg, e);
        }
    }
    private void safeTermOrAck(Message msg) {
        try {
            if (msg.isJetStream()) {
                try {
                    msg.term();
                } catch (UnsupportedOperationException | IllegalStateException ex) {
                    msg.ack();
                }
            }
        } catch (Exception e) {
            logOnError("TERM/ACK", msg, e);
        }
    }
    private void safeNack(Message msg) {
        try {
            if (msg.isJetStream()) msg.nak();
        } catch (Exception e) {
            logOnError("NACK", msg, e);
        }
    }
    private void logOnError(String action, Message msg, Exception e) {
        if (!(e instanceof IllegalStateException && e.getMessage()!=null && e.getMessage().contains("Connection closed"))) {
            log.warn("NATS Failed to {} message sid={} subj={}: {}", action, msg.getSID(), msg.getSubject(), e.getMessage());
        }
    }

    private void unsubscribeSafely(Dispatcher d, Subscription sub, String context) {
        if (sub != null && sub.isActive()) {
            try {
                if (d != null && d.isActive()) {
                    d.unsubscribe(sub);
                } else {
                    sub.unsubscribe();
                }

                if (sub.isActive()) {
                    log.warn("{} | Subscription still active after first attempt, trying direct unsubscribe again.", context);
                    sub.unsubscribe();
                }
            } catch (IllegalStateException ise) {
                String msgText = ise.getMessage();
                if (msgText != null && (msgText.contains("Connection closed") || msgText.contains("Dispatcher inactive") || msgText.contains("Subscription closed"))) {
                    log.trace("{} | Ignored IllegalStateException during unsubscribe (likely closed/inactive): {}", context, msgText);
                } else {
                    log.warn("{} | Unexpected IllegalStateException during unsubscribe: {}", context, msgText);
                }
            } catch (Exception e) {
                log.warn("{} | Error during unsubscribe: {}", context, e.getMessage());
            }
        }
    }

    private void unsubscribeAndClose(Dispatcher d, Subscription sub, String ctx) {
        unsubscribeSafely(d, sub, ctx);
        try {
            if (d != null) nc.closeDispatcher(d);
        } catch (Exception e) {
            log.warn("{} | closeDispatcher failed: {}", ctx, e.getMessage());
        }
    }
}
