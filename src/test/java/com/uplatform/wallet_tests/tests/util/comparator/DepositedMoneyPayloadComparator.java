package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsDepositStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class DepositedMoneyPayloadComparator implements PayloadComparatorStrategy {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            NatsEventType.DEPOSITED_MONEY.getHeaderValue()
    );

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsDepositedMoneyPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload,
                                 Object natsPayload,
                                 long seqNum,
                                 String actualEventType) {
        NatsDepositedMoneyPayload kafka = (NatsDepositedMoneyPayload) deserializedKafkaPayload;
        NatsDepositedMoneyPayload nats = (NatsDepositedMoneyPayload) natsPayload;

        if (!Objects.equals(kafka.uuid(), nats.uuid())) {
            logMismatch(seqNum, "uuid", kafka.uuid(), nats.uuid(), actualEventType);
            return false;
        }
        if (!Objects.equals(kafka.currencyCode(), nats.currencyCode())) {
            logMismatch(seqNum, "currency_code", kafka.currencyCode(), nats.currencyCode(), actualEventType);
            return false;
        }
        if (PayloadComparatorStrategy.compareBigDecimals(kafka.amount(), nats.amount()) != 0) {
            logMismatch(seqNum, "amount", kafka.amount(), nats.amount(), actualEventType);
            return false;
        }
        if (!Objects.equals(kafka.status(), nats.status())) {
            logMismatch(seqNum, "status", kafka.status(), nats.status(), actualEventType);
            return false;
        }
        if (!Objects.equals(kafka.nodeUuid(), nats.nodeUuid())) {
            logMismatch(seqNum, "node_uuid", kafka.nodeUuid(), nats.nodeUuid(), actualEventType);
            return false;
        }
        if (!Objects.equals(kafka.bonusId(), nats.bonusId())) {
            logMismatch(seqNum, "bonus_id", kafka.bonusId(), nats.bonusId(), actualEventType);
            return false;
        }
        return true;
    }
}
