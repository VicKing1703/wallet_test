package com.uplatform.wallet_tests.tests.wallet.betting.loss;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponCalcStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponType;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.model.enums.IFrameRecordType;
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
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка перерасчёта win на loss с отправкой события recalculated_from_iframe.
 *
 * Сначала делается ставка и получается win, после чего сумма пересчитывается на loss.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание пользователя с балансом.</li>
 *   <li><b>Основное действие:</b> ставка, win и перерасчёт на loss.</li>
 *   <li><b>Проверка ответа API:</b> для каждой операции статус 200 и успех.</li>
 *   <li><b>Проверка NATS:</b> recalculated_from_iframe.</li>
 *   <li><b>Проверка Kafka:</b> wallet.v8.projectionSource.</li>
 *   <li><b>Проверка БД:</b> betting_projection_iframe_history.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 *   <li>NATS: recalculated_from_iframe</li>
 *   <li>Kafka: wallet.v8.projectionSource</li>
 *   <li>База данных Wallet</li>
 *   <li>Redis кошелька</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Позитивные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
class RecalculatingWinLossParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> couponProvider() {
        return Stream.of(
                Arguments.of(NatsBettingCouponType.SINGLE, "Перерасчет Win->Loss с купоном SINGLE"),
                Arguments.of(NatsBettingCouponType.EXPRESS, "Перерасчет Win->Loss с купоном EXPRESS"),
                Arguments.of(NatsBettingCouponType.SYSTEM, "Перерасчет Win->Loss с купоном SYSTEM")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("couponProvider")
    @DisplayName("Проверка обработки перерасчета Win -> Loss iframe")
    void shouldProcessRecalculationFromWinToLoss(NatsBettingCouponType couponType, String description) {
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal betAmount = new BigDecimal("10.15");
        final BigDecimal winAmount = new BigDecimal("20.15");
        final BigDecimal lossAmount = new BigDecimal("0.00");
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsBettingEventPayload> recalculatedEvent;
            BigDecimal expectedBalance;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedBalance = adjustmentAmount.subtract(betAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            ctx.betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.walletData().playerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(couponType)
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .build();

            ctx.betRequestBody = generateRequest(ctx.betInputData);
            var response = managerClient.makePayment(ctx.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().success(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().errorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().description(), "manager_api.body.description")
            );
        });

        step("Manager API: Получение выигрыша", () -> {
            ctx.betRequestBody.setSumm(winAmount.toString());
            ctx.betRequestBody.setType(NatsBettingTransactionOperation.WIN);
            var response = managerClient.makePayment(ctx.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().success(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().errorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().description(), "manager_api.body.description")
            );
        });

        step("Manager API: Перерасчет результата на проигрыш", () -> {
            ctx.betRequestBody.setSumm(lossAmount.toString());
            ctx.betRequestBody.setType(NatsBettingTransactionOperation.LOSS);
            var response = managerClient.makePayment(ctx.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().success(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().errorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().description(), "manager_api.body.description")
            );
        });

        step("NATS: Проверка поступления события recalculated_from_iframe", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            var expectedBetInfoList = objectMapper.readValue(
                    ctx.betRequestBody.getBetInfo(),
                    new TypeReference<List<NatsBettingEventPayload.BetInfoDetail>>() {});
            var expectedBetInfo = expectedBetInfoList.get(0);

            ctx.recalculatedEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.RECALCULATED_FROM_IFRAME.getHeaderValue())
                    .with("$.bet_id", ctx.betRequestBody.getBetId())
                    .fetch();

            var actualPayload = ctx.recalculatedEvent.getPayload();
            assertAll("Проверка основных полей NATS payload",
                    () -> assertNotNull(actualPayload.uuid(), "nats.payload.uuid"),
                    () -> assertEquals(ctx.betRequestBody.getType(), actualPayload.type(), "nats.payload.type"),
                    () -> assertEquals(ctx.betRequestBody.getBetId(), actualPayload.betId(), "nats.payload.bet_id"),
                    () -> assertEquals(0, winAmount.negate().compareTo(actualPayload.amount()), "nats.payload.amount"),
                    () -> assertEquals(0, lossAmount.compareTo(actualPayload.rawAmount()), "nats.payload.raw_amount"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequestBody.getTotalCoef()).compareTo(actualPayload.totalCoeff()), "nats.payload.total_coeff"),
                    () -> assertTrue(Math.abs(ctx.betRequestBody.getTime() - actualPayload.time()) <= 10, "nats.payload.time"),
                    () -> assertNotNull(actualPayload.createdAt(), "nats.payload.created_at"),
                    () -> assertTrue(actualPayload.wageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info")
            );

            var actualBetInfo = actualPayload.betInfo().get(0);
            assertAll("Проверка полей внутри bet_info NATS payload",
                    () -> assertEquals(expectedBetInfo.champId(), actualBetInfo.champId(), "nats.payload.bet_info.champId"),
                    () -> assertEquals(expectedBetInfo.champName(), actualBetInfo.champName(), "nats.payload.bet_info.champ_name"),
                    () -> assertEquals(0, expectedBetInfo.coef().compareTo(actualBetInfo.coef()), "nats.payload.bet_info.coef"),
                    () -> assertEquals(expectedBetInfo.couponType(), actualBetInfo.couponType(), "nats.payload.bet_info.coupon_type"),
                    () -> assertEquals(expectedBetInfo.dateStart(), actualBetInfo.dateStart(), "nats.payload.bet_info.date_start"),
                    () -> assertEquals(expectedBetInfo.event(), actualBetInfo.event(), "nats.payload.bet_info.event"),
                    () -> assertEquals(expectedBetInfo.gameName(), actualBetInfo.gameName(), "nats.payload.bet_info.game_name"),
                    () -> assertEquals(expectedBetInfo.score(), actualBetInfo.score(), "nats.payload.bet_info.score"),
                    () -> assertEquals(expectedBetInfo.sportName(), actualBetInfo.sportName(), "nats.payload.bet_info.sport_name")
            );
        });

        step("Kafka: Проверка поступления сообщения recalculated_from_iframe в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.recalculatedEvent.getSequence())
                    .fetch();
            assertTrue(utils.areEquivalent(kafkaMessage, ctx.recalculatedEvent), "kafka.payload");
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    ctx.registeredPlayer.walletData().playerUUID());
            var player = ctx.registeredPlayer.walletData();
            assertAll("Проверка трешхолда после получения перерасчета",
                    () -> assertEquals(player.playerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, betAmount.negate().compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("DB Wallet: Проверка записи в таблице betting_projection_iframe_history", () -> {
            var dbTransaction = walletDatabaseClient.findLatestIframeHistoryByUuidOrFail(
                    ctx.recalculatedEvent.getPayload().uuid());

            var recalculatedEventPayload = ctx.recalculatedEvent.getPayload();
            var player = ctx.registeredPlayer.walletData();
            var betInfo = recalculatedEventPayload.betInfo().get(0);

            var actualDbBetInfoList = objectMapper
                    .readValue(dbTransaction.getBetInfo(),
                            new TypeReference<List<NatsBettingEventPayload.BetInfoDetail>>() {});

            assertAll("Проверка записанной строки в таблицу с историей ставок на спорт",
                    () -> assertEquals(recalculatedEventPayload.uuid(), dbTransaction.getUuid(), "db.iframe_history.uuid"),
                    () -> assertEquals(player.walletUUID(), dbTransaction.getWalletUuid(), "db.iframe_history.wallet_uuid"),
                    () -> assertEquals(player.playerUUID(), dbTransaction.getPlayerUuid(), "db.iframe_history.player_uuid"),
                    () -> assertEquals(CouponType.valueOf(couponType.name()), dbTransaction.getCouponType(), "db.iframe_history.coupon_type"),
                    () -> assertEquals(CouponStatus.LOSS, dbTransaction.getCouponStatus(),  "db.iframe_history.coupon_status"),
                    () -> assertEquals(CouponCalcStatus.RECALCULATED, dbTransaction.getCouponCalcStatus(),  "db.iframe_history.coupon_calc_status"),
                    () -> assertEquals(recalculatedEventPayload.betId(), dbTransaction.getBetId(), "db.iframe_history.bet_id"),
                    () -> assertEquals(recalculatedEventPayload.betInfo(), actualDbBetInfoList, "db.iframe_history.bet_info"),
                    () -> assertEquals(0, betAmount.compareTo(dbTransaction.getAmount()), "db.iframe_history.amount"),
                    () -> assertNotNull(dbTransaction.getBetTime(), "db.iframe_history.bet_time"),
                    () -> assertNotNull(dbTransaction.getModifiedAt(), "db.iframe_history.modified_at"),
                    () -> assertNotNull(dbTransaction.getCreatedAt(), "db.iframe_history.created_at"),
                    () -> assertEquals(ctx.recalculatedEvent.getSequence(), dbTransaction.getSeq(), "db.iframe_history.seq"),
                    () -> {
                        var expectedCoeff = new BigDecimal(ctx.betRequestBody.getTotalCoef());
                        assertEquals(0, expectedCoeff.compareTo(dbTransaction.getTotalCoeff()), "db.iframe_history.total_coeff");
                    },
                    () -> {
                        var expectedCoeff = new BigDecimal(ctx.betRequestBody.getTotalCoef());
                        assertEquals(0, expectedCoeff.compareTo(dbTransaction.getPrevCoeff()), "db.iframe_history.prev_coeff");
                    },
                    () -> {
                        var expectedCoeff = new BigDecimal(ctx.betRequestBody.getTotalCoef());
                        assertEquals(0, expectedCoeff.compareTo(dbTransaction.getSourceCoeff()), "db.iframe_history.source_coeff");
                    },
                    () -> assertEquals(0, recalculatedEventPayload.amount().compareTo(dbTransaction.getAmountDelta()), "db.iframe_history.amount_delta"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(dbTransaction.getWinSum()), "db.iframe_history.win_sum"),
                    () -> assertNotNull(dbTransaction.getCouponCreatedAt(), "db.iframe_history.coupon_created_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.recalculatedEvent.getSequence())
                    .fetch();

            var actualBetInfo = aggregate.iFrameRecords().get(2);
            var expectedBetInfo = ctx.recalculatedEvent.getPayload();

            assertAll("Проверка изменения агрегата, после обработки ставки",
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.availableWithdrawalBalance()), "redis.aggregate.available_withdrawal_balance"),
                    () -> assertEquals(expectedBetInfo.uuid(), actualBetInfo.uuid(), "redis.aggregate.iframe.uuid"),
                    () -> assertEquals(expectedBetInfo.betId(), actualBetInfo.getBetID(), "redis.aggregate.iframe.bet_id"),
                    () -> assertEquals(expectedBetInfo.amount(), actualBetInfo.getAmount(), "redis.aggregate.iframe.amount"),
                    () -> assertEquals(0, expectedBetInfo.totalCoeff().compareTo(actualBetInfo.getTotalCoeff()), "redis.aggregate.iframe.total_coeff"),
                    () -> assertNotNull(actualBetInfo.getTime(), "redis.aggregate.iframe.time"),
                    () -> assertNotNull(actualBetInfo.getCreatedAt(), "redis.aggregate.iframe.created_at"),
                    () -> assertEquals(IFrameRecordType.LOSS, actualBetInfo.getType(), "redis.aggregate.iframe.type")
            );
        });
    }
}
