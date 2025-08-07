package com.uplatform.wallet_tests.api.attachment;

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
