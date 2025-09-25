package com.uplatform.wallet_tests.api.nats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.uplatform.wallet_tests.api.attachment.AttachmentType;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NatsAttachmentHelper {

    private final ObjectMapper objectMapper;
    private final AllureAttachmentService attachmentService;

    public <T> void addNatsAttachment(String name, NatsMessage<T> natsMsg) {
        StringBuilder sb = new StringBuilder();

        if (natsMsg == null) {
            log.warn("Attaching placeholder for '{}' because message is null.", name);
            sb.append("Message: <null>\n");
        } else {
            sb.append("Metadata:\n");
            sb.append(" - Subject: ").append(natsMsg.getSubject()).append("\n");
            sb.append(" - Sequence: ").append(natsMsg.getSequence()).append("\n");
            if (natsMsg.getType() != null) {
                sb.append(" - Type Header: ").append(natsMsg.getType()).append("\n");
            }
            if (natsMsg.getTimestamp() != null) {
                sb.append(" - Timestamp: ")
                        .append(natsMsg.getTimestamp().toInstant())
                        .append(" (")
                        .append(natsMsg.getTimestamp())
                        .append(")\n");
            }

            T payload = natsMsg.getPayload();
            if (payload != null) {
                sb.append('\n');
                sb.append("Payload:");
                sb.append("\n - Data Type: ").append(payload.getClass().getName()).append("\n\n");
                try {
                    sb.append("Payload (JSON):\n");
                    sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
                } catch (JsonProcessingException e) {
                    sb.append("Error marshalling payload: ").append(e.getMessage()).append("\n");
                    sb.append("Payload (toString()):\n").append(payload);
                }
            } else {
                sb.append("\nPayload: <null>\n");
            }
        }

        try {
            attachmentService.attachText(AttachmentType.NATS, name, sb.toString());
        } catch (Exception e) {
            log.error("Failed to add Allure attachment '{}': {}", name, e.getMessage());
        }
    }

    public void addSearchInfo(String subject,
                              Class<?> messageType,
                              Duration timeout,
                              Map<String, Object> payloadFilters,
                              Map<String, Object> metadataFilters,
                              boolean unique,
                              Duration duplicateWindow,
                              boolean legacyPredicateUsed) {
        StringBuilder builder = new StringBuilder();
        builder.append("Subject: ").append(subject).append('\n');
        builder.append("Message Type: ")
                .append(messageType != null ? messageType.getSimpleName() : "N/A")
                .append('\n');
        if (timeout != null) {
            builder.append("Timeout: ").append(timeout.toMillis()).append(" ms\n");
        }
        builder.append("Mode: ").append(unique ? "unique" : "first-match");
        if (unique) {
            builder.append(" (window=")
                    .append(duplicateWindow != null ? duplicateWindow.toMillis() : "default")
                    .append(" ms)");
        }
        builder.append('\n');

        if (metadataFilters != null && !metadataFilters.isEmpty()) {
            builder.append("Metadata Filters:");
            metadataFilters.forEach((key, value) -> builder
                    .append('\n')
                    .append(" - ")
                    .append(key)
                    .append(" = ")
                    .append(String.valueOf(value)));
            builder.append('\n');
        } else {
            builder.append("Metadata Filters: [none]\n");
        }

        if (payloadFilters != null && !payloadFilters.isEmpty()) {
            builder.append("Payload Filters:");
            payloadFilters.forEach((key, value) -> builder
                    .append('\n')
                    .append(" - ")
                    .append(key)
                    .append(" = ")
                    .append(String.valueOf(value)));
            builder.append('\n');
        } else {
            builder.append("Payload Filters: [none]\n");
        }

        builder.append("Legacy Predicate: ")
                .append(legacyPredicateUsed ? "enabled" : "not used");

        try {
            attachmentService.attachText(AttachmentType.NATS, "Search Info", builder.toString());
        } catch (Exception e) {
            log.error("Failed to add NATS Search Info attachment: {}", e.getMessage());
        }
    }
}
