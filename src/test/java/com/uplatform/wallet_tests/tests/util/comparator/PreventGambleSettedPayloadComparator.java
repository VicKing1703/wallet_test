package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsPreventGambleSettedPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class PreventGambleSettedPayloadComparator implements PayloadComparatorStrategy {

    private static final String EVENT_TYPE = "setting_prevent_gamble_setted";
    private static final Set<String> SUPPORTED_TYPES = Set.of(EVENT_TYPE);

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsPreventGambleSettedPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload,
                                 Object natsPayload,
                                 long seqNum,
                                 String actualEventType) {
        var kafka = (NatsPreventGambleSettedPayload) deserializedKafkaPayload;
        var nats  = (NatsPreventGambleSettedPayload) natsPayload;
        boolean ok = true;

        if (kafka.gamblingActive() != nats.gamblingActive()) {
            logMismatch(seqNum,
                    "isGamblingActive",
                    kafka.gamblingActive(),
                    nats.gamblingActive(),
                    actualEventType);
            ok = false;
        }
        if (kafka.bettingActive() != nats.bettingActive()) {
            logMismatch(seqNum,
                    "isBettingActive",
                    kafka.bettingActive(),
                    nats.bettingActive(),
                    actualEventType);
            ok = false;
        }
        if (kafka.createdAt() != nats.createdAt()) {
            logMismatch(seqNum,
                    "createdAt",
                    kafka.createdAt(),
                    nats.createdAt(),
                    actualEventType);
            ok = false;
        }

        return ok;
    }
}
