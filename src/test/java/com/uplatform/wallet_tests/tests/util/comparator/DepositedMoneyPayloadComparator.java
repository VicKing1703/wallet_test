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

        if (!Objects.equals(kafka.getUuid(), nats.getUuid())) {
            logMismatch(seqNum, "uuid", kafka.getUuid(), nats.getUuid(), actualEventType);
            return false;
        }
        if (!Objects.equals(kafka.getCurrencyCode(), nats.getCurrencyCode())) {
            logMismatch(seqNum, "currency_code", kafka.getCurrencyCode(), nats.getCurrencyCode(), actualEventType);
            return false;
        }
        if (PayloadComparatorStrategy.compareBigDecimals(kafka.getAmount(), nats.getAmount()) != 0) {
            logMismatch(seqNum, "amount", kafka.getAmount(), nats.getAmount(), actualEventType);
            return false;
        }
        if (!Objects.equals(kafka.getStatus(), nats.getStatus())) {
            logMismatch(seqNum, "status", kafka.getStatus(), nats.getStatus(), actualEventType);
            return false;
        }
        if (!Objects.equals(kafka.getNodeUuid(), nats.getNodeUuid())) {
            logMismatch(seqNum, "node_uuid", kafka.getNodeUuid(), nats.getNodeUuid(), actualEventType);
            return false;
        }
        if (!Objects.equals(kafka.getBonusId(), nats.getBonusId())) {
            logMismatch(seqNum, "bonus_id", kafka.getBonusId(), nats.getBonusId(), actualEventType);
            return false;
        }
        return true;
    }
}
