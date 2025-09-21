package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
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
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * Этот тест проверяет, что возврат (рефанд) ставки на спорт через iFrame
 * не отменяет уже засчитанный прогресс отыгрыша депозита.
 *
 * <h3>Сценарий: Неизменность отыгрыша при рефанде ставки</h3>
 * <p>Проверяется, что после рефанда баланс игрока восстанавливается, а сумма
 * отыгрыша депозита остается неизменной.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует зарегистрированный и верифицированный игрок.</li>
 *   <li>Игрок сделал депозит, который требует отыгрыша.</li>
 *   <li>Игрок сделал ставку на спорт, которая начала отыгрыш депозита.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Выполняется возврат (рефанд) этой ставки через эндпоинт `makePayment`.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>manager_api</b>: Отвечает статусом <code>200 OK</code> на запрос рефанда.</li>
 *   <li><b>wallet-manager</b>: Отправляет событие <code>refunded_from_iframe</code> в NATS, которое <strong>не содержит</strong> блок <code>wagered_deposit_info</code>.</li>
 *   <li><b>wallet_wallet_redis</b>: Обновляет агрегат кошелька в Redis:
 *   <ul><li>Баланс кошелька восстанавливается до исходной суммы депозита.</li>
 *       <li>Внутреннее поле <code>wageringAmount</code> у депозита равно сумме ставки и <strong>не изменилось</strong> после рефанда.</li></ul></li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Отыгрыш депозита")
@Tag("Wallet") @Tag("Payment")
class DepositWageringBetRefundFromIframeTest extends BaseTest {

    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal BET_AMOUNT = new BigDecimal("10.15");

    @Test
    @DisplayName("Влияние транзакций типа makePayment (REFUND) на отыгрыш депозита")
    void shouldDepositBetRefundFromIframeAndCheckRedis() {

        final class TestData {
            RegisteredPlayerData player;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            MakePaymentRequest betRequest;
            NatsMessage<NatsBettingEventPayload> refundEvent;

            BigDecimal expectedBalanceAfterRefund;
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
                        .matching((p, t) -> NatsEventType.DEPOSITED_MONEY.getHeaderValue().equals(t))
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
                        .matching((p, t) -> NatsEventType.BETTED_FROM_IFRAME.getHeaderValue().equals(t))
                        .fetch();

                assertNotNull(betEvent, "precondition.nats.bet_event.not_found");
            });

            step("Подготовка ожидаемых результатов для последующих проверок", () -> {
                ctx.expectedBalanceAfterRefund = DEPOSIT_AMOUNT;
                ctx.expectedWageredAmount = BET_AMOUNT;
            });
        });

        step("WHEN: Игрок выполняет возврат (рефанд) ставки через manager_api", () -> {
            ctx.betRequest.setType(NatsBettingTransactionOperation.REFUND);
            var response = managerClient.makePayment(ctx.betRequest);

            assertAll("Проверка ответа от manager_api",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.make_payment.body.success")
            );
        });

        step("THEN: wallet-manager отправляет событие `refunded_from_iframe` в NATS без wager_info", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().playerUUID(),
                    ctx.player.getWalletData().walletUUID());

            BiPredicate<NatsBettingEventPayload, String> filter = (payload, type) ->
                    NatsEventType.REFUNDED_FROM_IFRAME.getHeaderValue().equals(type) &&
                            ctx.betRequest.getBetId().equals(payload.getBetId());

            ctx.refundEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var payload = ctx.refundEvent.getPayload();
            assertAll("Проверка полей события рефанда в NATS",
                    () -> assertEquals(ctx.betRequest.getBetId(), payload.getBetId(), "nats.refund.bet_id"),
                    () -> assertEquals(NatsBettingTransactionOperation.REFUND, payload.getType(), "nats.refund.type"),
                    () -> assertEquals(0, BET_AMOUNT.compareTo(payload.getAmount()), "nats.refund.amount"),
                    () -> assertTrue(payload.getWageredDepositInfo().isEmpty(), "nats.refund.wagered_deposit_info.is_empty")
            );
        });

        step("THEN: wallet_wallet_redis восстанавливает баланс, но не меняет сумму отыгрыша", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.player.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.refundEvent.getSequence())
                    .fetch();

            var depositData = aggregate.deposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                    .findFirst().orElse(null);

            assertAll("Проверка агрегата кошелька в Redis после рефанда",
                    () -> assertEquals((int) ctx.refundEvent.getSequence(), aggregate.lastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterRefund.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertNotNull(depositData, "redis.aggregate.deposit_not_found"),
                    () -> assertEquals(0, ctx.expectedWageredAmount.compareTo(depositData.wageringAmount()), "redis.aggregate.deposit.wagering_amount_unchanged"),
                    () -> assertEquals(2, aggregate.iFrameRecords().size(), "redis.aggregate.iframe_records.count"),
                    () -> assertEquals(IFrameRecordType.REFUND, aggregate.iFrameRecords().get(1).type(), "redis.aggregate.iframe_records.refund_type")
            );
        });
    }
}
