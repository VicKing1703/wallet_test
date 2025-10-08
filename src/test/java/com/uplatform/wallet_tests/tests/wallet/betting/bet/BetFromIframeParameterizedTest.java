package com.uplatform.wallet_tests.tests.wallet.betting.bet;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponCalcStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponType;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.model.enums.IFrameRecordType;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий полный цикл обработки ставки на спорт, инициированной через iFrame.
 * Тестируемый эндпоинт: {@code POST /_wallet_manager/api/v1/wallet/iframe-callback/make-payment}.
 *
 * <p><b>Идея теста:</b> Убедиться, что система корректно обрабатывает ставку игрока от момента получения запроса
 * до конечного обновления данных во всех связанных подсистемах (NATS, Kafka, DB, Redis). Тест параметризован для
 * покрытия всех основных типов купонов (SINGLE, EXPRESS, SYSTEM), подтверждая консистентность обработки.</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока с начальным положительным балансом.</li>
 *   <li>Отправка запроса на совершение ставки через эндпоинт {@code makePayment} в manager_api.</li>
 *   <li>Проверка успешного ответа от manager_api (HTTP 200 OK).</li>
 *   <li>Прослушивание и валидация NATS-события типа {@link NatsEventType#BETTED_FROM_IFRAME}.</li>
 *   <li>Проверка того, что NATS-событие было успешно спроецировано в Kafka-топик.</li>
 *   <li>Проверка обновления данных в таблицах основной БД: {@code threshold} и {@code betting_projection_iframe_history}.</li>
 *   <li>Проверка конечного состояния кошелька в Redis: уменьшение баланса и добавление записи о ставке.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос на ставку успешно обрабатывается.</li>
 *   <li>В NATS и Kafka публикуются корректные события с данными о ставке.</li>
 *   <li>В базе данных создаются соответствующие записи о транзакции и обновляется порог.</li>
 *   <li>Баланс игрока в Redis корректно уменьшается на сумму ставки.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("MakePayment: Позитивные сценарии")
@Tag("Betting") @Tag("Wallet")
class BetFromIframeParameterizedTest extends BaseParameterizedTest {
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("150.00");
    private static final BigDecimal BET_AMOUNT = new BigDecimal("10.15");

    static Stream<Arguments> couponProvider() {
        return Stream.of(
                Arguments.of(NatsBettingCouponType.SINGLE, 1),
                Arguments.of(NatsBettingCouponType.EXPRESS, 2),
                Arguments.of(NatsBettingCouponType.SYSTEM, 3)
        );
    }

    @ParameterizedTest(name = "Ставка с купоном: {0}")
    @MethodSource("couponProvider")
    @DisplayName("Полный цикл обработки ставки из iFrame")
    void shouldPlaceBetFromIframeWithCoupon(
            NatsBettingCouponType couponType,
            int expectedBetInfoSize
    ) {
        final class TestContext {
            RegisteredPlayerData player;
            MakePaymentRequest betRequest;
            NatsMessage<NatsBettingEventPayload> betEvent;
            BigDecimal expectedBalanceAfterBet;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя с начальным балансом", () -> {
            ctx.player = defaultTestSteps.registerNewPlayer(INITIAL_BALANCE);
            assertNotNull(ctx.player.walletData(), "default_step.registration.wallet_data");
            ctx.expectedBalanceAfterBet = INITIAL_BALANCE.subtract(BET_AMOUNT);
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            var betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.player.walletData().playerUUID())
                    .summ(BET_AMOUNT.toPlainString())
                    .couponType(couponType)
                    .currency(ctx.player.walletData().currency())
                    .build();

            ctx.betRequest = generateRequest(betInputData);
            var response = managerClient.makePayment(ctx.betRequest);

            assertAll("Проверка ответа от manager_api",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.make_payment.response_body"),
                    () -> assertTrue(response.getBody().success(), "manager_api.make_payment.body.success")
            );
        });

        step("NATS: Проверка события betted_from_iframe", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.walletData().playerUUID(),
                    ctx.player.walletData().walletUUID());

            ctx.betEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_IFRAME.getHeaderValue())
                    .with("bet_id", ctx.betRequest.getBetId())
                    .unique()
                    .fetch();

            var payload = ctx.betEvent.getPayload();
            assertAll("Проверка полей NATS-события",
                    () -> assertEquals(ctx.betRequest.getBetId(), payload.betId(), "nats.betted_from_iframe.bet_id"),
                    () -> assertEquals(0, BET_AMOUNT.negate().compareTo(payload.amount()), "nats.betted_from_iframe.amount"),
                    () -> assertEquals(expectedBetInfoSize, payload.betInfo().size(), "nats.betted_from_iframe.bet_info.size")
            );

            payload.betInfo().forEach(bi ->
                    assertEquals(couponType.getValue(), bi.couponType(), "nats.betted_from_iframe.bet_info.coupon_type"));
        });

        step("Kafka: Проверка проекции события betted_from_iframe в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("type", ctx.betEvent.getType())
                    .with("seq_number", ctx.betEvent.getSequence())
                    .unique()
                    .fetch();

            var natsPayload = ctx.betEvent.getPayload();
            var kafkaPayloadParsed = assertDoesNotThrow(() ->
                    objectMapper.readValue(kafkaMessage.payload(), NatsBettingEventPayload.class));

            assertAll("Проверка полей Kafka-сообщения",
                    () -> assertEquals(ctx.betEvent.getType(), kafkaMessage.type(), "kafka.betted_from_iframe.type"),
                    () -> assertEquals(ctx.betEvent.getSequence(), kafkaMessage.seqNumber(), "kafka.betted_from_iframe.seq_number"),
                    () -> assertEquals(natsPayload.uuid(), kafkaPayloadParsed.uuid(), "kafka.betted_from_iframe.payload.uuid"),
                    () -> assertEquals(natsPayload.betId(), kafkaPayloadParsed.betId(), "kafka.betted_from_iframe.payload.bet_id"),
                    () -> assertEquals(0, natsPayload.amount().compareTo(kafkaPayloadParsed.amount()), "kafka.betted_from_iframe.payload.amount"),
                    () -> assertEquals(0, natsPayload.rawAmount().compareTo(kafkaPayloadParsed.rawAmount()), "kafka.betted_from_iframe.payload.raw_amount"),
                    () -> assertEquals(0, natsPayload.totalCoeff().compareTo(kafkaPayloadParsed.totalCoeff()), "kafka.betted_from_iframe.payload.total_coeff"),
                    () -> assertNotNull(kafkaMessage.seqNumberNodeUuid(), "kafka.betted_from_iframe.seq_number_node_uuid"),
                    () -> assertEquals(ctx.betEvent.getTimestamp().toEpochSecond(), kafkaMessage.timestamp(), "kafka.betted_from_iframe.timestamp")
            );
        });

        step("DB (Threshold): Проверка обновления порога выигрыша", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    ctx.player.walletData().playerUUID());

            assertAll("Проверка записи в таблице порогов",
                    () -> assertEquals(ctx.player.walletData().playerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, BET_AMOUNT.negate().compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("DB (History): Проверка записи в betting_projection_iframe_history", () -> {
            var dbTransaction = walletDatabaseClient.findLatestIframeHistoryByUuidOrFail(
                    ctx.betEvent.getPayload().uuid());

            assertAll("Проверка записи в таблице истории",
                    () -> assertEquals(ctx.betEvent.getPayload().uuid(), dbTransaction.getUuid(), "db.history.uuid"),
                    () -> assertEquals(ctx.player.walletData().walletUUID(), dbTransaction.getWalletUuid(), "db.history.wallet_uuid"),
                    () -> assertEquals(CouponType.valueOf(couponType.name()), dbTransaction.getCouponType(), "db.history.coupon_type"),
                    () -> assertEquals(CouponStatus.ACCEPTED, dbTransaction.getCouponStatus(), "db.history.coupon_status"),
                    () -> assertEquals(CouponCalcStatus.NO, dbTransaction.getCouponCalcStatus(), "db.history.coupon_calc_status"),
                    () -> assertEquals(0, BET_AMOUNT.compareTo(dbTransaction.getAmount()), "db.history.amount"),
                    () -> assertEquals(ctx.betEvent.getSequence(), dbTransaction.getSeq(), "db.history.seq")
            );
        });

        step("Redis (Wallet): Проверка обновления состояния кошелька", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.player.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", ctx.betEvent.getSequence())
                    .fetch();

            var iframeRecord = aggregate.iFrameRecords().stream()
                    .filter(r -> r.uuid().equals(ctx.betEvent.getPayload().uuid()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Запись о ставке не найдена в агрегате Redis"));

            assertAll("Проверка агрегата кошелька в Redis",
                    () -> assertEquals(ctx.betEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterBet.compareTo(aggregate.balance()), "redis.wallet.balance"),
                    () -> assertEquals(ctx.betEvent.getPayload().betId(), iframeRecord.getBetID(), "redis.wallet.iframe_record.bet_id"),
                    () -> assertEquals(IFrameRecordType.BET, iframeRecord.getType(), "redis.wallet.iframe_record.type")
            );
        });
    }
}