package com.testing.multisource.api.db;

import jakarta.annotation.PostConstruct;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import com.testing.multisource.api.db.exceptions.DatabaseQueryTimeoutException;
import com.testing.multisource.api.db.exceptions.DatabaseRecordNotFoundException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.testing.multisource.api.attachment.AllureAttachmentService;
import com.testing.multisource.api.attachment.AttachmentType;
import static org.awaitility.Awaitility.await;

public abstract class AbstractDatabaseClient {

    protected final AllureAttachmentService attachmentService;

    protected AbstractDatabaseClient(AllureAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Value("${app.db.retry-timeout-seconds}")
    private long retryTimeoutSeconds;
    @Value("${app.db.retry-poll-interval-ms}")
    private long retryPollIntervalMillis;
    @Value("${app.db.retry-poll-delay-ms}")
    private long retryPollDelayMillis;

    protected Duration retryTimeoutDuration;
    protected Duration retryPollIntervalDuration;
    protected Duration retryPollDelayDuration;

    @PostConstruct
    public void initializeAwaitilityConfig() {
        this.retryTimeoutDuration = Duration.ofSeconds(retryTimeoutSeconds);
        this.retryPollIntervalDuration = Duration.ofMillis(retryPollIntervalMillis);
        this.retryPollDelayDuration = Duration.ofMillis(retryPollDelayMillis);
    }

    @SafeVarargs
    protected final <T> T awaitAndGetOrFail(String description,
                                           String attachmentNamePrefix,
                                           Supplier<Optional<T>> querySupplier,
                                           Class<? extends Throwable>... ignoredExceptionsDuringAwait) {
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier,
                retryTimeoutDuration, ignoredExceptionsDuringAwait);
    }

    @SafeVarargs
    protected final <T> T awaitAndGetOrFail(String description,
                                           String attachmentNamePrefix,
                                           Supplier<Optional<T>> querySupplier,
                                           Duration timeout,
                                           Class<? extends Throwable>... ignoredExceptionsDuringAwait) {
        Callable<Optional<T>> queryCallable = querySupplier::get;

        try {
            ConditionFactory condition = await(description)
                    .atMost(timeout)
                    .pollInterval(retryPollIntervalDuration)
                    .pollDelay(retryPollDelayDuration)
                    .ignoreExceptionsInstanceOf(org.springframework.dao.TransientDataAccessException.class);

            if (ignoredExceptionsDuringAwait != null) {
                for (Class<? extends Throwable> ignored : ignoredExceptionsDuringAwait) {
                    if (ignored != null) {
                        condition = condition.ignoreExceptionsInstanceOf(ignored);
                    }
                }
            }

            Optional<T> optionalResult = condition.until(queryCallable, Optional::isPresent);

            T result = optionalResult.get();
            attachmentService.attachJson(AttachmentType.DB, attachmentNamePrefix + " - Found", result);
            return result;

        } catch (ConditionTimeoutException e) {
            attachmentService.attachText(AttachmentType.DB, attachmentNamePrefix + " - NOT Found (Timeout)",
                    "Timeout after " + timeout + ": " + e.getMessage());
            throw new DatabaseRecordNotFoundException("Record not found within timeout for '" + description + "'", e);
        } catch (Exception e) {
            attachmentService.attachText(AttachmentType.DB, attachmentNamePrefix + " - Error",
                    "Error type: " + e.getClass().getName() + "\nMessage: " + e.getMessage());
            throw new DatabaseQueryTimeoutException("Unexpected error during DB await for '" + description + "'", e);
        }
    }


}

