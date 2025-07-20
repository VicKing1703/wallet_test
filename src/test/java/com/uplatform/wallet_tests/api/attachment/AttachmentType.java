package com.uplatform.wallet_tests.api.attachment;

/**
 * Standard prefixes for Allure attachments. Using a common set makes reports
 * easier to read and navigate.
 */
public enum AttachmentType {
    HTTP("HTTP"),
    KAFKA("Kafka"),
    REDIS("Redis"),
    DB("DB"),
    NATS("NATS");

    private final String prefix;

    AttachmentType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
