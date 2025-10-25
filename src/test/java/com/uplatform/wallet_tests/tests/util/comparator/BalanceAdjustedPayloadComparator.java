package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsBalanceAdjustedPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class BalanceAdjustedPayloadComparator implements PayloadComparatorStrategy {

    private static final String EVENT_TYPE = "balance_adjusted";
    private static final Set<String> SUPPORTED_TYPES = Set.of(EVENT_TYPE);

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsBalanceAdjustedPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        NatsBalanceAdjustedPayload kafka = (NatsBalanceAdjustedPayload) deserializedKafkaPayload;
        NatsBalanceAdjustedPayload nats = (NatsBalanceAdjustedPayload) natsPayload;

        if (!Objects.equals(kafka, nats)) {
            log.debug("Comparison failed (SeqNum: {}): Payload objects are not equal using DTO.equals() for type '{}'.",
                    seqNum, actualEventType);
            logDetailedDifference(kafka, nats, seqNum, actualEventType);
            return false;
        }
        return true;
    }

    private void logDetailedDifference(NatsBalanceAdjustedPayload kafka, NatsBalanceAdjustedPayload nats, long seqNum, String actualEventType) {
        log.debug("Detailed payload comparison for {} (SeqNum: {})", actualEventType, seqNum);

        checkAndLogMismatch(seqNum, "UUID", kafka.uuid(), nats.uuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Currency", kafka.currency(), nats.currency(), actualEventType);

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.amount(), nats.amount()) != 0) {
            logMismatch(seqNum, "Amount (value)", kafka.amount(), nats.amount(), actualEventType);
        } else if (!Objects.equals(kafka.amount(), nats.amount())) {
            log.trace("Amount values equal via compareTo but differ in scale (SeqNum: {}). Kafka: {}, Nats: {}",
                    seqNum, kafka.amount(), nats.amount());
            logMismatch(seqNum, "Amount (scale differs)", kafka.amount(), nats.amount(), actualEventType);
        }

        if (kafka.operationType() != nats.operationType()) {
            logMismatch(seqNum, "Operation Type", kafka.operationType(), nats.operationType(), actualEventType);
        }
        if (kafka.direction() != nats.direction()) {
            logMismatch(seqNum, "Direction", kafka.direction(), nats.direction(), actualEventType);
        }
        if (kafka.reason() != nats.reason()) {
            logMismatch(seqNum, "Reason", kafka.reason(), nats.reason(), actualEventType);
        }
        checkAndLogMismatch(seqNum, "Comment", kafka.comment(), nats.comment(), actualEventType);
        checkAndLogMismatch(seqNum, "User UUID", kafka.userUuid(), nats.userUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "User Name", kafka.userName(), nats.userName(), actualEventType);
    }
}