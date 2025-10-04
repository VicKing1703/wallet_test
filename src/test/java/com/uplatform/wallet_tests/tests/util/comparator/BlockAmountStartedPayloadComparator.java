package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsBlockAmountEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class BlockAmountStartedPayloadComparator implements PayloadComparatorStrategy {
    
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            NatsEventType.BLOCK_AMOUNT_STARTED.getHeaderValue()
    );

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsBlockAmountEventPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        NatsBlockAmountEventPayload kafka = (NatsBlockAmountEventPayload) deserializedKafkaPayload;
        NatsBlockAmountEventPayload nats = (NatsBlockAmountEventPayload) natsPayload;

        if (!Objects.equals(kafka, nats)) {
            logDetailedDifference(kafka, nats, seqNum, actualEventType);
            return false;
        }
        return true;
    }

    private void logDetailedDifference(NatsBlockAmountEventPayload kafka, NatsBlockAmountEventPayload nats, long seqNum, String actualEventType) {
        checkAndLogMismatch(seqNum, "UUID", kafka.uuid(), nats.uuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Status", kafka.status(), nats.status(), actualEventType);

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.amount(), nats.amount()) != 0) {
            logMismatch(seqNum, "Amount (value)", kafka.amount(), nats.amount(), actualEventType);
        } else if (!Objects.equals(kafka.amount(), nats.amount())) {
            logMismatch(seqNum, "Amount (scale differs)", kafka.amount(), nats.amount(), actualEventType);
        }

        checkAndLogMismatch(seqNum, "Reason", kafka.reason(), nats.reason(), actualEventType);
        checkAndLogMismatch(seqNum, "Type", kafka.type(), nats.type(), actualEventType);
        checkAndLogMismatch(seqNum, "User UUID", kafka.userUuid(), nats.userUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "User Name", kafka.userName(), nats.userName(), actualEventType);

        if (!Objects.equals(kafka.createdAt(), nats.createdAt())) {
            logMismatch(seqNum, "Created At", kafka.createdAt(), nats.createdAt(), actualEventType);
        }

        if (!Objects.equals(kafka.expiredAt(), nats.expiredAt())) {
            logMismatch(seqNum, "Expired At", kafka.expiredAt(), nats.expiredAt(), actualEventType);
        }
    }
}
