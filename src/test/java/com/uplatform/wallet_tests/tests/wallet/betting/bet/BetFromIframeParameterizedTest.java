package com.uplatform.wallet_tests.tests.wallet.betting.bet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponCalcStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponType;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
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
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * Этот параметризованный тест проверяет полный цикл обработки ставки на спорт,
 * совершенной через iFrame, с различными типами купонов.
 *
 * <h3>Сценарий: Полный цикл обработки ставки из iFrame</h3>
 * <p>Проверяется, что система корректно обрабатывает запрос от manager_api,
 * отправляет события в NATS и Kafka, обновляет данные в БД и конечное состояние кошелька в Redis.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует зарегистрированный игрок с положительным балансом.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Игрок совершает ставку на спорт через эндпоинт `makePayment` с купоном типа SINGLE, EXPRESS или SYSTEM.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>manager_api</b>: Отвечает статусом <code>200 OK</code> на запрос <code>makePayment</code>.</li>
 *   <li><b>wallet-manager</b>: Обрабатывает ставку и отправляет событие <code>betted_from_iframe</code> в NATS.</li>
 *   <li><b>wallet_projections_nats_to_kafka</b>: Пересылает событие из NATS в Kafka для downstream-сервисов.</li>
 *   <li><b>wallet_database</b>: Обновляет таблицы <code>threshold</code> и <code>betting_projection_iframe_history</code>.</li>
 *   <li><b>wallet_wallet_redis</b>: Обновляет агрегат кошелька (ключ <code>wallet:{uuid}</code>), уменьшая баланс и добавляя запись о ставке.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Позитивные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
class BetFromIframeParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("150.00");
    private static final BigDecimal BET_AMOUNT = new BigDecimal("10.15");

    static Stream<Arguments> couponProvider() {
        return Stream.of(
                Arguments.of(NatsBettingCouponType.SINGLE, 1, "Ставка с купоном SINGLE"),
                Arguments.of(NatsBettingCouponType.EXPRESS, 2, "Ставка с купоном EXPRESS"),
                Arguments.of(NatsBettingCouponType.SYSTEM, 3, "Ставка с купоном SYSTEM")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("couponProvider")
    @DisplayName("Проверка обработки ставки iframe с купоном")
    void shouldPlaceBetFromIframeWithCoupon(
            NatsBettingCouponType couponType,
            int expectedBetInfoSize,
            String description) throws Exception {

        final class TestData {
            RegisteredPlayerData player;
            MakePaymentRequest betRequest;
            NatsMessage<NatsBettingEventPayload> betEvent;
            BigDecimal expectedBalanceAfterBet;
        }
        final TestData ctx = new TestData();

        step("GIVEN: Игрок с положительным балансом", () -> {
            step("Регистрация нового игрока с начальным балансом", () -> {
                ctx.player = defaultTestSteps.registerNewPlayer(INITIAL_BALANCE);
                assertNotNull(ctx.player, "setup.player.creation");
            });

            step("Подготовка ожидаемых результатов для последующих проверок", () -> {
                ctx.expectedBalanceAfterBet = INITIAL_BALANCE.subtract(BET_AMOUNT);
            });
        });

        step("WHEN: Игрок совершает ставку на спорт через manager_api", () -> {
            var betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.player.getWalletData().playerUUID())
                    .summ(BET_AMOUNT.toPlainString())
                    .couponType(couponType)
                    .currency(ctx.player.getWalletData().currency())
                    .build();

            ctx.betRequest = generateRequest(betInputData);
            var response = managerClient.makePayment(ctx.betRequest);

            assertAll("Проверка ответа от manager_api",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.make_payment.body.success")
            );
        });

        step("THEN: Система корректно обрабатывает ставку во всех компонентах", () -> {
            step("wallet-manager отправляет событие `betted_from_iframe` в NATS", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                BiPredicate<NatsBettingEventPayload, String> filter = (payload, type) ->
                        NatsEventType.BETTED_FROM_IFRAME.getHeaderValue().equals(type) &&
                                Objects.equals(ctx.betRequest.getBetId(), payload.getBetId());

                ctx.betEvent = natsClient.expect(NatsBettingEventPayload.class)
                        .from(subject)
                        .matching(filter)
                        .fetch();

                assertNotNull(ctx.betEvent, "nats.bet_event.not_found");

                var payload = ctx.betEvent.getPayload();
                assertAll("Проверка полей события ставки в NATS",
                        () -> assertEquals(ctx.betRequest.getBetId(), payload.getBetId(), "nats.payload.bet_id"),
                        () -> assertEquals(0, new BigDecimal(ctx.betRequest.getSumm()).negate().compareTo(payload.getAmount()), "nats.payload.amount"),
                        () -> assertEquals(expectedBetInfoSize, payload.getBetInfo().size(), "nats.payload.bet_info.size")
                );

                payload.getBetInfo().forEach(bi ->
                        assertEquals(couponType.getValue(), bi.getCouponType(), "nats.payload.bet_info.coupon_type"));
            });

            step("AND: Kafka получает соответствующее сообщение", () -> {
                var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                        .with("seq_number", ctx.betEvent.getSequence())
                        .fetch();

                assertTrue(utils.areEquivalent(kafkaMessage, ctx.betEvent), "kafka.message.payload_match");
            });

            step("AND: В БД обновляется порог выигрыша", () -> {
                var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                        ctx.player.getWalletData().playerUUID());

                assertAll("Проверка записи в таблице порогов (thresholds)",
                        () -> assertEquals(ctx.player.getWalletData().playerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                        () -> assertEquals(0, BET_AMOUNT.negate().compareTo(threshold.getAmount()), "db.threshold.amount"),
                        () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
                );
            });

            step("AND: Запись сохраняется в betting_projection_iframe_history", () -> {
                var dbTransaction = walletDatabaseClient.findLatestIframeHistoryByUuidOrFail(
                        ctx.betEvent.getPayload().getUuid());

                var betEventPayload = ctx.betEvent.getPayload();
                var playerData = ctx.player.getWalletData();

                var actualDbBetInfoList = objectMapper.readValue(
                        dbTransaction.getBetInfo(), new TypeReference<List<NatsBettingEventPayload.BetInfoDetail>>() {});

                assertAll("Проверка записи в таблице истории (iframe_history)",
                        () -> assertEquals(betEventPayload.getUuid(), dbTransaction.getUuid(), "db.history.uuid"),
                        () -> assertEquals(playerData.walletUUID(), dbTransaction.getWalletUuid(), "db.history.wallet_uuid"),
                        () -> assertEquals(playerData.playerUUID(), dbTransaction.getPlayerUuid(), "db.history.player_uuid"),
                        () -> assertEquals(CouponType.valueOf(couponType.name()), dbTransaction.getCouponType(), "db.history.coupon_type"),
                        () -> assertEquals(CouponStatus.ACCEPTED, dbTransaction.getCouponStatus(), "db.history.coupon_status"),
                        () -> assertEquals(CouponCalcStatus.NO, dbTransaction.getCouponCalcStatus(), "db.history.coupon_calc_status"),
                        () -> assertEquals(betEventPayload.getBetId(), dbTransaction.getBetId(), "db.history.bet_id"),
                        () -> assertEquals(0, betEventPayload.getAmount().compareTo(dbTransaction.getAmount().negate()), "db.history.amount"),
                        () -> assertEquals(ctx.betEvent.getSequence(), dbTransaction.getSeq(), "db.history.seq")
                );
            });

            step("AND: В Redis обновляется агрегат кошелька", () -> {
                var aggregate = redisClient.getWalletDataWithSeqCheck(
                        ctx.player.getWalletData().walletUUID(),
                        (int) ctx.betEvent.getSequence());

                var iframeRecord = aggregate.iFrameRecords().stream()
                        .filter(r -> r.getUuid().equals(ctx.betEvent.getPayload().getUuid()))
                        .findFirst().orElse(null);

                assertAll("Проверка агрегата кошелька в Redis после ставки",
                        () -> assertEquals((int) ctx.betEvent.getSequence(), aggregate.lastSeqNumber(), "redis.aggregate.last_seq_number"),
                        () -> assertEquals(0, ctx.expectedBalanceAfterBet.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                        () -> assertNotNull(iframeRecord, "redis.aggregate.iframe_record_not_found"),
                        () -> assertEquals(ctx.betEvent.getPayload().getBetId(), iframeRecord.getBetID(), "redis.aggregate.iframe_record.bet_id"),
                        () -> assertEquals(IFrameRecordType.BET, iframeRecord.getType(), "redis.aggregate.iframe_record.type")
                );
            });
        });
    }
}
