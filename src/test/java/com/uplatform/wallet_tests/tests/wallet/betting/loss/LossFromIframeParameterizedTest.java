package com.uplatform.wallet_tests.tests.wallet.betting.loss;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponCalcStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponType;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
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
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет обработку проигрыша ставки из iframe.
 *
 * Тест создаёт игрока, делает ставку через makePayment, а затем регистрирует loss.
 * Далее проверяется событие loosed_from_iframe и обновления в БД и Redis.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание пользователя и кошелька.</li>
 *   <li><b>Основное действие:</b> ставка, затем получение loss.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и успешное тело.</li>
 *   <li><b>Проверка NATS:</b> событие loosed_from_iframe.</li>
 *   <li><b>Проверка Kafka:</b> wallet.v8.projectionSource.</li>
 *   <li><b>Проверка БД:</b> betting_projection_iframe_history.</li>
 *   <li><b>Проверка Redis:</b> обновление агрегата.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 *   <li>NATS: событие loosed_from_iframe</li>
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
class LossFromIframeParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> couponProvider() {
        return Stream.of(
                Arguments.of(NatsBettingCouponType.SINGLE, "Проигрыш из iframe с купоном SINGLE"),
                Arguments.of(NatsBettingCouponType.EXPRESS, "Проигрыш из iframe с купоном EXPRESS"),
                Arguments.of(NatsBettingCouponType.SYSTEM, "Проигрыш из iframe с купоном SYSTEM")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("couponProvider")
    @DisplayName("Проверка обработки проигрыша iframe")
    void shouldProcessLossFromIframeAndVerifyEvent(NatsBettingCouponType couponType, String description) {
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal betAmount = new BigDecimal("10.15");
        final BigDecimal lossAmount = BigDecimal.ZERO;
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsBettingEventPayload> lossNatsEvent;
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
                    .playerId(ctx.registeredPlayer.getWalletData().playerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(couponType)
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .build();

            ctx.betRequestBody = generateRequest(ctx.betInputData);
            var response = managerClient.makePayment(ctx.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.body.description")
            );
        });

        step("Manager API: Получение проигрыша", () -> {
            ctx.betRequestBody.setSumm(lossAmount.toString());
            ctx.betRequestBody.setType(NatsBettingTransactionOperation.LOSS);
            var response = managerClient.makePayment(ctx.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.body.description")
            );
        });

        step("NATS: Проверка поступления события loosed_from_iframe", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());

            BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LOOSED_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                            Objects.equals(ctx.betRequestBody.getBetId(), payload.getBetId());

            ctx.lossNatsEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var actualPayload = ctx.lossNatsEvent.getPayload();
            var expectedBetInfoList = objectMapper.readValue(
                    ctx.betRequestBody.getBetInfo(),
                    new TypeReference<List<NatsBettingEventPayload.BetInfoDetail>>() {});
            assertAll("Проверка основных полей NATS payload",
                    () -> assertNotNull(actualPayload.getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(ctx.betRequestBody.getType(), actualPayload.getType(), "nats.payload.type"),
                    () -> assertEquals(ctx.betRequestBody.getBetId(), actualPayload.getBetId(), "nats.payload.bet_id"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequestBody.getSumm()).compareTo(actualPayload.getAmount()), "nats.payload.amount"),
                    () -> assertNotNull(actualPayload.getRawAmount(), "nats.payload.raw_amount"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequestBody.getSumm()).compareTo(actualPayload.getRawAmount()), "nats.payload.raw_amount"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequestBody.getTotalCoef()).compareTo(actualPayload.getTotalCoeff()), "nats.payload.total_coeff"),
                    () -> assertTrue(Math.abs(ctx.betRequestBody.getTime() - actualPayload.getTime()) <= 10, "nats.payload.time"),
                    () -> assertNotNull(actualPayload.getCreatedAt(), "nats.payload.created_at"),
                    () -> assertTrue(actualPayload.getWageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info")
            );

            var expectedBetInfo = expectedBetInfoList.get(0);
            var actualBetInfo = actualPayload.getBetInfo().get(0);
            assertAll("Проверка полей внутри bet_info NATS payload",
                    () -> assertEquals(expectedBetInfo.getChampId(), actualBetInfo.getChampId(), "nats.payload.bet_info.champId"),
                    () -> assertEquals(expectedBetInfo.getChampName(), actualBetInfo.getChampName(), "nats.payload.bet_info.champ_name"),
                    () -> assertEquals(0, expectedBetInfo.getCoef().compareTo(actualBetInfo.getCoef()), "nats.payload.bet_info.coef"),
                    () -> assertEquals(expectedBetInfo.getCouponType(), actualBetInfo.getCouponType(), "nats.payload.bet_info.coupon_type"),
                    () -> assertEquals(expectedBetInfo.getDateStart(), actualBetInfo.getDateStart(), "nats.payload.bet_info.date_start"),
                    () -> assertEquals(expectedBetInfo.getEvent(), actualBetInfo.getEvent(), "nats.payload.bet_info.event"),
                    () -> assertEquals(expectedBetInfo.getGameName(), actualBetInfo.getGameName(), "nats.payload.bet_info.game_name"),
                    () -> assertEquals(expectedBetInfo.getScore(), actualBetInfo.getScore(), "nats.payload.bet_info.score"),
                    () -> assertEquals(expectedBetInfo.getSportName(), actualBetInfo.getSportName(), "nats.payload.bet_info.sport_name")
            );
        });

        step("Kafka: Проверка поступления сообщения loosed_from_iframe в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.lossNatsEvent.getSequence())
                    .fetch();
            assertTrue(utils.areEquivalent(kafkaMessage, ctx.lossNatsEvent), "kafka.payload");
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    ctx.registeredPlayer.getWalletData().playerUUID());
            var player = ctx.registeredPlayer.getWalletData();
            var expectedAmount = betAmount.negate();
            assertAll("Проверка трешхолда после совершения ставки на спорт",
                    () -> assertEquals(player.playerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, expectedAmount.compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("DB Wallet: Проверка записи в таблице betting_projection_iframe_history", () -> {
            var dbTransaction = walletDatabaseClient.findLatestIframeHistoryByUuidOrFail(
                    ctx.lossNatsEvent.getPayload().getUuid());

            var lossEventPayload = ctx.lossNatsEvent.getPayload();
            var player = ctx.registeredPlayer.getWalletData();
            var betInfo = lossEventPayload.getBetInfo().get(0);

            var actualDbBetInfoList = objectMapper
                    .readValue(dbTransaction.getBetInfo(),
                            new TypeReference<List<NatsBettingEventPayload.BetInfoDetail>>() {});

            assertAll("Проверка записанной строки в таблицу с историей ставок на спорт",
                    () -> assertEquals(lossEventPayload.getUuid(), dbTransaction.getUuid(), "db.iframe_history.uuid"),
                    () -> assertEquals(player.walletUUID(), dbTransaction.getWalletUuid(), "db.iframe_history.wallet_uuid"),
                    () -> assertEquals(player.playerUUID(), dbTransaction.getPlayerUuid(), "db.iframe_history.player_uuid"),
                    () -> assertEquals(CouponType.valueOf(couponType.name()), dbTransaction.getCouponType(), "db.iframe_history.coupon_type"),
                    () -> assertEquals(CouponStatus.LOSS, dbTransaction.getCouponStatus(),  "db.iframe_history.coupon_status"),
                    () -> assertEquals(CouponCalcStatus.CALCULATED, dbTransaction.getCouponCalcStatus(),  "db.iframe_history.coupon_calc_status"),
                    () -> assertEquals(lossEventPayload.getBetId(), dbTransaction.getBetId(), "db.iframe_history.bet_id"),
                    () -> assertEquals(lossEventPayload.getBetInfo(), actualDbBetInfoList, "db.iframe_history.bet_info"),
                    () -> assertEquals(0, betAmount.compareTo(dbTransaction.getAmount()), "db.iframe_history.amount"),
                    () -> assertNotNull(dbTransaction.getBetTime(), "db.iframe_history.bet_time"),
                    () -> assertNotNull(dbTransaction.getModifiedAt(), "db.iframe_history.modified_at"),
                    () -> assertNotNull(dbTransaction.getCreatedAt(), "db.iframe_history.created_at"),
                    () -> assertEquals(ctx.lossNatsEvent.getSequence(), dbTransaction.getSeq(), "db.iframe_history.seq"),
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
                    () -> assertEquals(0, lossEventPayload.getAmount().compareTo(dbTransaction.getAmountDelta()), "db.iframe_history.amount_delta"),
                    () -> assertEquals(0, lossAmount.compareTo(dbTransaction.getWinSum()), "db.iframe_history.win_sum"),
                    () -> assertNotNull(dbTransaction.getCouponCreatedAt(), "db.iframe_history.coupon_created_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.lossNatsEvent.getSequence())
                    .fetch();

            var actualBetInfo = aggregate.iFrameRecords().get(1);
            var expectedBetInfo = ctx.lossNatsEvent.getPayload();

            assertAll("Проверка изменения агрегата, после обработки ставки",
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.availableWithdrawalBalance()), "redis.aggregate.available_withdrawal_balance"),
                    () -> assertEquals(expectedBetInfo.getUuid(), actualBetInfo.getUuid(), "redis.aggregate.iframe.uuid"),
                    () -> assertEquals(expectedBetInfo.getBetId(), actualBetInfo.getBetID(), "redis.aggregate.iframe.bet_id"),
                    () -> assertEquals(expectedBetInfo.getAmount(), actualBetInfo.getAmount(), "redis.aggregate.iframe.amount"),
                    () -> assertEquals(0, expectedBetInfo.getTotalCoeff().compareTo(actualBetInfo.getTotalCoeff()), "redis.aggregate.iframe.total_coeff"),
                    () -> assertNotNull(actualBetInfo.getTime(), "redis.aggregate.iframe.time"),
                    () -> assertNotNull(actualBetInfo.getCreatedAt(), "redis.aggregate.iframe.created_at"),
                    () -> assertEquals(IFrameRecordType.LOSS, actualBetInfo.getType(), "redis.aggregate.iframe.type")
            );
        });
    }
}
