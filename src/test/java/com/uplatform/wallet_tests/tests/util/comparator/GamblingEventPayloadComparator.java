package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class GamblingEventPayloadComparator implements PayloadComparatorStrategy {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "won_from_gamble",
            "betted_from_gamble",
            "refunded_from_gamble",
            "rollbacked_from_gamble",
            "tournament_won_from_gamble"
    );

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsGamblingEventPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        NatsGamblingEventPayload kafka = (NatsGamblingEventPayload) deserializedKafkaPayload;
        NatsGamblingEventPayload nats = (NatsGamblingEventPayload) natsPayload;

        if (!Objects.equals(kafka, nats)) {
            logDetailedDifference(kafka, nats, seqNum, actualEventType);
            return false;
        }
        return true;
    }

    private void logDetailedDifference(NatsGamblingEventPayload kafka, NatsGamblingEventPayload nats, long seqNum, String actualEventType) {
        checkAndLogMismatch(seqNum, "Payload Transaction UUID", kafka.uuid(), nats.uuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Bet UUID", kafka.betUuid(), nats.betUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Game Session UUID", kafka.gameSessionUuid(), nats.gameSessionUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Provider Round ID", kafka.providerRoundId(), nats.providerRoundId(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Currency", kafka.currency(), nats.currency(), actualEventType);

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.amount(), nats.amount()) != 0) {
            logMismatch(seqNum, "Payload Amount (value)", kafka.amount(), nats.amount(), actualEventType);
        } else if (!Objects.equals(kafka.amount(), nats.amount())) {
            logMismatch(seqNum, "Payload Amount (scale differs)", kafka.amount(), nats.amount(), actualEventType);
        }

        checkAndLogMismatch(seqNum, "Payload Type (internal)", kafka.type(), nats.type(), actualEventType);
        if (kafka.providerRoundClosed() != nats.providerRoundClosed()) {
            logMismatch(seqNum, "Payload Provider Round Closed", kafka.providerRoundClosed(), nats.providerRoundClosed(), actualEventType);
        }
        checkAndLogMismatch(seqNum, "Payload Message", kafka.message(), nats.message(), actualEventType);
        if (!Objects.equals(kafka.createdAt(), nats.createdAt())) {
            logMismatch(seqNum, "Payload CreatedAt Timestamp", kafka.createdAt(), nats.createdAt(), actualEventType);
        }
        checkAndLogMismatch(seqNum, "Payload Direction", kafka.direction(), nats.direction(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Operation", kafka.operation(), nats.operation(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Node UUID", kafka.nodeUuid(), nats.nodeUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Game UUID", kafka.gameUuid(), nats.gameUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Provider UUID", kafka.providerUuid(), nats.providerUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Wagered Deposit Info", kafka.wageredDepositInfo(), nats.wageredDepositInfo(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Currency Conversion Info", kafka.currencyConversionInfo(), nats.currencyConversionInfo(), actualEventType);
    }
}