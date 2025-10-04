package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
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
import java.util.Map;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * Этот параметризованный тест проверяет ключевой сценарий отыгрыша депозита
 * при совершении ставки на спорт через iFrame (эндпоинт `makePayment`).
 *
 * <h3>Сценарий: Отыгрыш депозита через ставку на спорт</h3>
 * <p>Проверяется, что система корректно обновляет сумму отыгрыша депозита
 * после того, как игрок делает ставку через Manager API.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует зарегистрированный и верифицированный игрок.</li>
 *   <li>Игрок сделал депозит, который требует отыгрыша.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Игрок совершает ставку на спорт через эндпоинт `makePayment`.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>manager_api</b>: Отвечает статусом <code>200 OK</code> на запрос.</li>
 *   <li><b>wallet-manager</b>: Отправляет событие <code>betted_from_iframe</code> в NATS.
 *   <ul><li>Событие содержит поле <code>wagered_deposit_info</code> с информацией о том, какая часть какого депозита была отыграна.</li></ul></li>
 *   <li><b>wallet_wallet_redis</b>: Обновляет агрегат кошелька в Redis.
 *   <ul><li>Общий баланс кошелька уменьшается на сумму ставки.</li>
 *       <li>Внутреннее поле <code>wageringAmount</code> у депозита увеличивается на сумму ставки.</li></ul></li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Отыгрыш депозита")
@Tag("Wallet") @Tag("Payment")
class DepositWageringBetFromIframeParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("150.00");

    static Stream<Arguments> betAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(DEPOSIT_AMOUNT),
                        "BET на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        "BET на нулевую сумму"),
                Arguments.of(
                        DEPOSIT_AMOUNT,
                        "BET на всю сумму депозита")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("betAmountProvider")
    @DisplayName("Влияние транзакций типа makePayment на отыгрыш депозита:")
    void shouldDepositAndBetFromIframeAndCheckRedis(BigDecimal betAmount, String description) {

        final class TestData {
            RegisteredPlayerData player;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            MakePaymentRequest betRequest;
            NatsMessage<NatsBettingEventPayload> betEvent;

            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedWageredAmount;
        }
        final TestData ctx = new TestData();

        step("GIVEN: Игрок с активным депозитом", () -> {
            step("Регистрация нового игрока с KYC", () -> {
                ctx.player = defaultTestSteps.registerNewPlayerWithKyc();

                assertNotNull(ctx.player, "setup.player.creation");
            });

            step("Выполнение депозита через FAPI", () -> {
                var depositRequest = DepositRequestBody.builder()
                        .amount(DEPOSIT_AMOUNT.toPlainString())
                        .paymentMethodId(PaymentMethodId.FAKE)
                        .currency(ctx.player.getWalletData().currency())
                        .country(configProvider.getEnvironmentConfig().getPlatform().getCountry())
                        .redirect(DepositRequestBody.RedirectUrls.builder()
                                .failed(DepositRedirect.FAILED.url())
                                .success(DepositRedirect.SUCCESS.url())
                                .pending(DepositRedirect.PENDING.url())
                                .build())
                        .build();

                publicClient.deposit(ctx.player.getAuthorizationResponse().getBody().getToken(), depositRequest);
            });

            step("Проверка получения подтверждающего события о депозите в NATS", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                        .from(subject)
                        .withType(NatsEventType.DEPOSITED_MONEY.getHeaderValue())
                        .fetch();

                assertNotNull(ctx.depositEvent, "precondition.nats.deposit_event.not_found");
            });

            step("Подготовка ожидаемых результатов для последующих проверок", () -> {
                ctx.expectedBalanceAfterBet = DEPOSIT_AMOUNT.subtract(betAmount);
                ctx.expectedWageredAmount = betAmount;
            });
        });

        step("WHEN: Игрок совершает ставку на спорт через manager_api", () -> {
            var betInput = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.player.getWalletData().playerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(ctx.player.getWalletData().currency())
                    .build();

            ctx.betRequest = generateRequest(betInput);

            var response = managerClient.makePayment(ctx.betRequest);

            assertAll("Проверка ответа от manager_api",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment.status_code"),
                    () -> assertTrue(response.getBody().success(), "manager_api.make_payment.body.success")
            );
        });

        step("THEN: wallet-manager отправляет событие `betted_from_iframe` в NATS", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().playerUUID(),
                    ctx.player.getWalletData().walletUUID());

            ctx.betEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_IFRAME.getHeaderValue())
                    .with("$.bet_id", ctx.betRequest.getBetId())
                    .fetch();

            var payload = ctx.betEvent.getPayload();
            assertAll("Проверка полей события ставки в NATS",
                    () -> assertEquals(ctx.betRequest.getBetId(), payload.getBetId(), "nats.bet.bet_id"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequest.getSumm()).negate().compareTo(payload.getAmount()), "nats.bet.amount")
            );

            var wagerInfoList = payload.wageredDepositInfo();
            assertFalse(wagerInfoList.isEmpty(), "nats.wager_info.empty_for_bet");
            @SuppressWarnings("unchecked")
            Map<String, Object> wagerInfo = (Map<String, Object>) wagerInfoList.get(0);

            assertAll("Проверка wagered_deposit_info в NATS",
                    () -> assertEquals(ctx.depositEvent.getPayload().uuid(), wagerInfo.get("deposit_uuid"), "nats.wager_info.deposit_uuid"),
                    () -> assertEquals(0, ctx.expectedWageredAmount.compareTo(new BigDecimal(wagerInfo.get("updated_wagered_amount").toString())), "nats.wager_info.updated_wagered_amount")
            );
        });

        step("THEN: wallet_wallet_redis обновляет баланс и сумму отыгрыша в агрегате Redis", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.player.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.betEvent.sequence())
                    .fetch();

            var depositData = aggregate.deposits().stream()
                    .filter(d -> d.uuid().equals(ctx.depositEvent.getPayload().uuid()))
                    .findFirst().orElse(null);

            var iframeRecord = aggregate.iFrameRecords().stream()
                    .filter(r -> r.uuid().equals(ctx.betEvent.getPayload().uuid()))
                    .findFirst().orElse(null);

            assertAll("Проверка агрегата кошелька в Redis после ставки",
                    () -> assertEquals((int) ctx.betEvent.sequence(), aggregate.lastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterBet.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertNotNull(depositData, "redis.aggregate.deposit_not_found"),
                    () -> assertEquals(0, ctx.expectedWageredAmount.compareTo(depositData.wageringAmount()), "redis.aggregate.deposit.wagering_amount"),
                    () -> assertNotNull(iframeRecord, "redis.aggregate.iframe_record_not_found"),
                    () -> assertEquals(IFrameRecordType.BET, iframeRecord.getType(), "redis.aggregate.iframe_record.type")
            );
        });
    }
}
