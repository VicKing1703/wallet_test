package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class BettingEventPayloadComparator implements PayloadComparatorStrategy {

    private static final String BETTED_EVENT_TYPE = "betted_from_iframe";
    private static final String WON_EVENT_TYPE = "won_from_iframe";
    private static final String LOSS_EVENT_TYPE = "loosed_from_iframe";
    private static final String REFUNDED_EVENT_TYPE = "refunded_from_iframe";
    private static final String RECALCULATED_EVENT_TYPE = "recalculated_from_iframe";
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            BETTED_EVENT_TYPE,
            WON_EVENT_TYPE,
            LOSS_EVENT_TYPE,
            REFUNDED_EVENT_TYPE,
            RECALCULATED_EVENT_TYPE
    );

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsBettingEventPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        NatsBettingEventPayload kafka = (NatsBettingEventPayload) deserializedKafkaPayload;
        NatsBettingEventPayload nats = (NatsBettingEventPayload) natsPayload;

        boolean areEqual = true;

        if (!Objects.equals(kafka.uuid(), nats.uuid())) {
            logMismatch(seqNum, "uuid", kafka.uuid(), nats.uuid(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.type(), nats.type())) {
            logMismatch(seqNum, "payload.type", kafka.type(), nats.type(), actualEventType);
            areEqual = false;
        }
        if (kafka.betId() != nats.betId()) {
            logMismatch(seqNum, "bet_id", kafka.betId(), nats.betId(), actualEventType);
            areEqual = false;
        }

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.amount(), nats.amount()) != 0) {
            logMismatch(seqNum, "amount", kafka.amount(), nats.amount(), actualEventType);
            areEqual = false;
        }
        if (PayloadComparatorStrategy.compareBigDecimals(kafka.rawAmount(), nats.rawAmount()) != 0) {
            logMismatch(seqNum, "raw_amount", kafka.rawAmount(), nats.rawAmount(), actualEventType);
            areEqual = false;
        }

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.totalCoeff(), nats.totalCoeff()) != 0) {
            logMismatch(seqNum, "total_coeff", kafka.totalCoeff(), nats.totalCoeff(), actualEventType);
            areEqual = false;
        }

        if (kafka.time() != nats.time()) {
            logMismatch(seqNum, "time", kafka.time(), nats.time(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.createdAt(), nats.createdAt())) {
            logMismatch(seqNum, "created_at", kafka.createdAt(), nats.createdAt(), actualEventType);
            areEqual = false;
        }

        boolean kafkaWageredEmpty = kafka.wageredDepositInfo() == null || kafka.wageredDepositInfo().isEmpty();
        boolean natsWageredEmpty = nats.wageredDepositInfo() == null || nats.wageredDepositInfo().isEmpty();
        if (kafkaWageredEmpty != natsWageredEmpty) {
            logMismatch(seqNum, "wagered_deposit_info (emptiness)",
                    kafkaWageredEmpty ? "empty/null" : "not empty",
                    natsWageredEmpty ? "empty/null" : "not empty",
                    actualEventType);
            areEqual = false;
        } else if (!kafkaWageredEmpty && !Objects.equals(kafka.wageredDepositInfo(), nats.wageredDepositInfo())) {
            logMismatch(seqNum, "wagered_deposit_info (content)",
                    kafka.wageredDepositInfo(), nats.wageredDepositInfo(), actualEventType);
            areEqual = false;
        }

        boolean betInfoCompared = compareBetInfoLists(kafka.betInfo(), nats.betInfo(), seqNum, actualEventType);
        if (!betInfoCompared) {
            areEqual = false;
        }

        if (!areEqual) {
            log.debug("Comparison finished with mismatches (SeqNum: {}, Type: {}).", seqNum, actualEventType);
        }

        return areEqual;
    }

    private boolean compareBetInfoLists(List<NatsBettingEventPayload.BetInfoDetail> kafkaList,
                                        List<NatsBettingEventPayload.BetInfoDetail> natsList,
                                        long seqNum, String actualEventType) {

        boolean bothNullOrEmpty = (kafkaList == null || kafkaList.isEmpty()) && (natsList == null || natsList.isEmpty());
        if (bothNullOrEmpty) {
            return true;
        }

        if (kafkaList == null || natsList == null) {
            logMismatch(seqNum, "bet_info list (existence)",
                    kafkaList == null ? "null" : "exists",
                    natsList == null ? "null" : "exists",
                    actualEventType);
            return false;
        }

        if (kafkaList.size() != natsList.size()) {
            logMismatch(seqNum, "bet_info list size", kafkaList.size(), natsList.size(), actualEventType);
            return false;
        }

        boolean listsAreEqual = true;
        for (int i = 0; i < kafkaList.size(); i++) {
            NatsBettingEventPayload.BetInfoDetail kafkaDetail = kafkaList.get(i);
            NatsBettingEventPayload.BetInfoDetail natsDetail = natsList.get(i);

            if (kafkaDetail == null || natsDetail == null) {
                logMismatch(seqNum, "bet_info item[" + i + "] (existence)",
                        kafkaDetail == null ? "null" : "exists",
                        natsDetail == null ? "null" : "exists",
                        actualEventType);
                listsAreEqual = false;
                continue;
            }

            if (!compareBetInfoDetail(kafkaDetail, natsDetail, seqNum, actualEventType, i)) {
                listsAreEqual = false;
            }
        }

        return listsAreEqual;
    }

    private boolean compareBetInfoDetail(NatsBettingEventPayload.BetInfoDetail kafka,
                                         NatsBettingEventPayload.BetInfoDetail nats,
                                         long seqNum, String actualEventType, int index) {
        boolean areEqual = true;
        String prefix = "bet_info[" + index + "].";

        if (!Objects.equals(kafka.champId(), nats.champId())) {
            logMismatch(seqNum, prefix + "ChampId", kafka.champId(), nats.champId(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.champName(), nats.champName())) {
            logMismatch(seqNum, prefix + "ChampName", kafka.champName(), nats.champName(), actualEventType);
            areEqual = false;
        }
        if (PayloadComparatorStrategy.compareBigDecimals(kafka.coef(), nats.coef()) != 0) {
            logMismatch(seqNum, prefix + "Coef", kafka.coef(), nats.coef(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.couponType(), nats.couponType())) {
            logMismatch(seqNum, prefix + "CouponType", kafka.couponType(), nats.couponType(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.dateStart(), nats.dateStart())) {
            logMismatch(seqNum, prefix + "DateStart", kafka.dateStart(), nats.dateStart(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.event(), nats.event())) {
            logMismatch(seqNum, prefix + "Event", kafka.event(), nats.event(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.gameName(), nats.gameName())) {
            logMismatch(seqNum, prefix + "GameName", kafka.gameName(), nats.gameName(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.score(), nats.score())) {
            logMismatch(seqNum, prefix + "Score", kafka.score(), nats.score(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.sportName(), nats.sportName())) {
            logMismatch(seqNum, prefix + "SportName", kafka.sportName(), nats.sportName(), actualEventType);
            areEqual = false;
        }

        return areEqual;
    }
}