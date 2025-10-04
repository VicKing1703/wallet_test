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
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * Этот тест проверяет, что получение выигрыша по ставке на спорт (через iFrame)
 * не влияет на уже накопленный прогресс отыгрыша депозита.
 *
 * <h3>Сценарий: Неизменность отыгрыша при выигрыше ставки</h3>
 * <p>Проверяется, что после получения выигрыша баланс игрока корректно увеличивается,
 * а сумма отыгрыша депозита остается неизменной.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует зарегистрированный и верифицированный игрок.</li>
 *   <li>Игрок сделал депозит, который требует отыгрыша.</li>
 *   <li>Игрок сделал ставку на спорт, которая начала отыгрыш депозита.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Игрок получает выигрыш по этой ставке через эндпоинт `makePayment`.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>manager_api</b>: Отвечает статусом <code>200 OK</code> на запрос выигрыша.</li>
 *   <li><b>wallet-manager</b>: Отправляет событие <code>won_from_iframe</code> в NATS, которое <strong>не содержит</strong> блок <code>wagered_deposit_info</code>.</li>
 *   <li><b>wallet_wallet_redis</b>: Обновляет агрегат кошелька в Redis:
 *   <ul><li>Баланс кошелька корректно рассчитан (<code>депозит - ставка + выигрыш</code>).</li>
 *       <li>Внутреннее поле <code>wageringAmount</code> у депозита равно сумме ставки и <strong>не изменилось</strong> после выигрыша.</li></ul></li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Отыгрыш депозита")
@Tag("Wallet") @Tag("Payment")
class DepositWageringBetWinFromIframeTest extends BaseTest {

    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal BET_AMOUNT = new BigDecimal("10.15");
    private static final BigDecimal WIN_AMOUNT = new BigDecimal("20.15");

    @Test
    @DisplayName("Влияние транзакций типа makePayment (WIN) на отыгрыш депозита")
    void shouldDepositBetWinFromIframeAndCheckRedis() {

        final class TestData {
            RegisteredPlayerData player;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            MakePaymentRequest betRequest;
            NatsMessage<NatsBettingEventPayload> winEvent;

            BigDecimal expectedBalanceAfterWin;
            BigDecimal expectedWageredAmount;
        }
        final TestData ctx = new TestData();

        step("GIVEN: Игрок с депозитом и сделанной ставкой на спорт", () -> {
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
                ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class).from(subject)
                        .withType(NatsEventType.DEPOSITED_MONEY.getHeaderValue())
                        .fetch();
                assertNotNull(ctx.depositEvent, "precondition.nats.deposit_event.not_found");
            });

            step("Совершение ставки для начала отыгрыша", () -> {
                var betInput = MakePaymentData.builder()
                        .type(NatsBettingTransactionOperation.BET)
                        .playerId(ctx.player.getWalletData().playerUUID())
                        .summ(BET_AMOUNT.toPlainString())
                        .couponType(NatsBettingCouponType.SINGLE)
                        .currency(ctx.player.getWalletData().currency())
                        .build();
                ctx.betRequest = generateRequest(betInput);
                managerClient.makePayment(ctx.betRequest);

                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());
                var betEvent = natsClient.expect(NatsBettingEventPayload.class).from(subject)
                        .withType(NatsEventType.BETTED_FROM_IFRAME.getHeaderValue())
                        .fetch();
                assertNotNull(betEvent, "precondition.nats.bet_event.not_found");
            });

            step("Подготовка ожидаемых результатов для последующих проверок", () -> {
                ctx.expectedBalanceAfterWin = DEPOSIT_AMOUNT.subtract(BET_AMOUNT).add(WIN_AMOUNT);
                ctx.expectedWageredAmount = BET_AMOUNT;
            });
        });

        step("WHEN: Игрок получает выигрыш через manager_api", () -> {
            ctx.betRequest.setSumm(WIN_AMOUNT.toPlainString());
            ctx.betRequest.setType(NatsBettingTransactionOperation.WIN);
            var response = managerClient.makePayment(ctx.betRequest);

            assertAll("Проверка ответа от manager_api",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment.status_code"),
                    () -> assertTrue(response.getBody().success(), "manager_api.make_payment.body.success")
            );
        });

        step("THEN: wallet-manager отправляет событие `won_from_iframe` в NATS без wager_info", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().playerUUID(),
                    ctx.player.getWalletData().walletUUID());

            ctx.winEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.WON_FROM_IFRAME.getHeaderValue())
                    .with("$.bet_id", ctx.betRequest.getBetId())
                    .fetch();

            var payload = ctx.winEvent.getPayload();
            assertAll("Проверка полей события выигрыша в NATS",
                    () -> assertEquals(ctx.betRequest.getBetId(), payload.getBetId(), "nats.win.bet_id"),
                    () -> assertEquals(NatsBettingTransactionOperation.WIN, payload.getType(), "nats.win.type"),
                    () -> assertEquals(0, WIN_AMOUNT.compareTo(payload.getAmount()), "nats.win.amount"),
                    () -> assertTrue(payload.getWageredDepositInfo().isEmpty(), "nats.win.wagered_deposit_info.is_empty")
            );
        });

        step("THEN: wallet_wallet_redis обновляет баланс, но не сумму отыгрыша", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.player.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.winEvent.getSequence())
                    .fetch();

            var depositData = aggregate.deposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                    .findFirst().orElse(null);

            assertAll("Проверка агрегата кошелька в Redis после выигрыша",
                    () -> assertEquals((int) ctx.winEvent.getSequence(), aggregate.lastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertNotNull(depositData, "redis.aggregate.deposit_not_found"),
                    () -> assertEquals(0, ctx.expectedWageredAmount.compareTo(depositData.wageringAmount()), "redis.aggregate.deposit.wagering_amount_unchanged"),
                    () -> assertEquals(2, aggregate.iFrameRecords().size(), "redis.aggregate.iframe_records.count"),
                    () -> assertEquals(IFrameRecordType.WIN, aggregate.iFrameRecords().get(1).type(), "redis.aggregate.iframe_records.win_type")
            );
        });
    }
}
