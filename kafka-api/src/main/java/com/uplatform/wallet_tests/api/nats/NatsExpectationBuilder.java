package com.uplatform.wallet_tests.api.nats;

import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsDeserializationException;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsDuplicateMessageException;
import com.uplatform.wallet_tests.api.nats.exceptions.NatsMessageNotFoundException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

public class NatsExpectationBuilder<T> {
    private final NatsClient client;
    private final Class<T> messageType;
    private final Duration defaultTimeout;
    private String subject;
    private BiPredicate<T, String> filter = (p, t) -> true;
    private boolean unique = false;
    private Duration timeout;
    private Duration duplicateWindow;

    public NatsExpectationBuilder(NatsClient client, Class<T> messageType, Duration defaultTimeout) {
        this.client = client;
        this.messageType = messageType;
        this.defaultTimeout = defaultTimeout;
    }

    public NatsExpectationBuilder<T> from(String subject) {
        this.subject = subject;
        return this;
    }

    public NatsExpectationBuilder<T> matching(BiPredicate<T, String> filter) {
        this.filter = filter;
        return this;
    }

    public NatsExpectationBuilder<T> unique() {
        this.unique = true;
        this.duplicateWindow = client.getDefaultUniqueWindow();
        return this;
    }

    public NatsExpectationBuilder<T> unique(Duration window) {
        this.unique = true;
        this.duplicateWindow = window;
        return this;
    }

    public NatsExpectationBuilder<T> within(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public CompletableFuture<NatsMessage<T>> fetchAsync() {
        if (subject == null) {
            throw new IllegalStateException("Subject must be specified");
        }
        Duration effectiveTimeout = this.timeout != null ? this.timeout : defaultTimeout;
        CompletableFuture<NatsMessage<T>> future = unique ?
                client.findUniqueMessageAsync(subject, messageType, filter,
                        (duplicateWindow != null ? duplicateWindow : client.getDefaultUniqueWindow())) :
                client.findMessageAsync(subject, messageType, filter);
        return future.orTimeout(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public NatsMessage<T> fetch() {
        try {
            return fetchAsync().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NatsMessageNotFoundException("Fetching NATS message interrupted", e);
        } catch (Exception e) {
            Throwable cause = e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
            if (cause instanceof NatsDuplicateMessageException) {
                throw (NatsDuplicateMessageException) cause;
            } else if (cause instanceof NatsDeserializationException) {
                throw (NatsDeserializationException) cause;
            } else {
                throw new NatsMessageNotFoundException("Failed to fetch NATS message", cause);
            }
        }
    }
}
