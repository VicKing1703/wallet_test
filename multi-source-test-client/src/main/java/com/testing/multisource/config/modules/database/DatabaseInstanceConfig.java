package com.testing.multisource.config.modules.database;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DatabaseInstanceConfig(
        String host,
        int port,
        String username,
        String password,
        @JsonProperty("retryTimeoutSeconds") int retryTimeoutSeconds,
        @JsonProperty("retryPollIntervalMs") long retryPollIntervalMs,
        @JsonProperty("retryPollDelayMs") long retryPollDelayMs
) {
    public int getRetryTimeoutSeconds() {
        return retryTimeoutSeconds;
    }

    public long getRetryPollIntervalMs() {
        return retryPollIntervalMs;
    }

    public long getRetryPollDelayMs() {
        return retryPollDelayMs;
    }
}
