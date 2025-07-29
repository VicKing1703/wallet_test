package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsDepositStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.model.enums.IFrameRecordType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет неизменность wagered_deposit_info после выигрыша ставки из iframe.
 *
 * Тест выполняет регистрацию, депозит, ставку и выплату выигрыша через makePayment.
 * В событии {@code won_from_iframe} блок {@code wagered_deposit_info} отсутствует,
 * а значение {@code WageringAmount} депозита не меняется. Баланс кошелька соответствует расчёту.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> полная регистрация с KYC.</li>
 *   <li><b>Депозит:</b> вызов FAPI эндпоинта deposit.</li>
 *   <li><b>Ставка:</b> отправка запроса makePayment.</li>
 *   <li><b>Выигрыш:</b> отправка запроса makePayment с типом WIN.</li>
 *   <li><b>Проверка NATS:</b> события deposited_money, betted_from_iframe и won_from_iframe.</li>
 *   <li><b>Проверка Redis:</b> депозит содержит неизменённый WageringAmount и корректный баланс.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: /deposit и makePayment</li>
 *   <li>NATS</li>
 *   <li>Redis кошелька</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Отыгрыш депозита")
@Tag("Wallet") @Tag("Payment")
class DepositWageringBetWinFromIframeTest extends BaseParameterizedTest {

    private static final BigDecimal depositAmount = new BigDecimal("150.00");
    private static final BigDecimal betAmount = new BigDecimal("10.15");
    private static final BigDecimal winAmount = new BigDecimal("20.15");

    @Test
    @DisplayName("Отыгрыш депозита: выигрыш ставки из iframe")
    void shouldDepositBetWinFromIframeAndCheckRedis() {
        final String nodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final class TestData {
            RegisteredPlayerData player;
            DepositRequestBody depositRequest;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            MakePaymentData betInput;
            MakePaymentRequest betRequest;
            NatsMessage<NatsBettingEventPayload> betEvent;
            NatsMessage<NatsBettingEventPayload> winEvent;
            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterWin;
        }
        final TestData ctx = new TestData();
        ctx.expectedBalanceAfterBet = depositAmount.subtract(betAmount);
        ctx.expectedBalanceAfterWin = ctx.expectedBalanceAfterBet.add(winAmount);

        step("Default Step: Полная регистрация игрока с KYC", () -> {
            ctx.player = defaultTestSteps.registerNewPlayerWithKyc();
            assertNotNull(ctx.player, "default_step.registration_with_kyc");
        });

        step("FAPI: Выполнение депозита", () -> {
            ctx.depositRequest = DepositRequestBody.builder()
                    .amount(depositAmount.toPlainString())
                    .paymentMethodId(PaymentMethodId.FAKE)
                    .currency(ctx.player.getWalletData().getCurrency())
                    .country(configProvider.getEnvironmentConfig().getPlatform().getCountry())
                    .redirect(DepositRequestBody.RedirectUrls.builder()
                            .failed(DepositRedirect.FAILED.url())
                            .success(DepositRedirect.SUCCESS.url())
                            .pending(DepositRedirect.PENDING.url())
                            .build())
                    .build();

            var response = publicClient.deposit(
                    ctx.player.getAuthorizationResponse().getBody().getToken(),
                    ctx.depositRequest);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.deposit.status_code");
        });

        step("NATS: Проверка события deposited_money", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().getPlayerUUID(),
                    ctx.player.getWalletData().getWalletUUID());

            BiPredicate<NatsDepositedMoneyPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.DEPOSITED_MONEY.getHeaderValue().equals(typeHeader);

            ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var payload = ctx.depositEvent.getPayload();
            assertAll("Проверка полей депозита",
                    () -> assertEquals(ctx.depositRequest.getCurrency(), payload.getCurrencyCode(), "nats.deposit.currency_code"),
                    () -> assertEquals(0, depositAmount.compareTo(payload.getAmount()), "nats.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS, payload.getStatus(), "nats.deposit.status"),
                    () -> assertEquals(nodeId, payload.getNodeUuid(), "nats.deposit.node_uuid")
            );
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            ctx.betInput = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.player.getWalletData().getPlayerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(ctx.player.getWalletData().getCurrency())
                    .build();

            ctx.betRequest = generateRequest(ctx.betInput);

            var response = managerClient.makePayment(ctx.betRequest);

            assertAll("Проверка ответа ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success")
            );
        });

        step("NATS: Проверка события betted_from_iframe", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().getPlayerUUID(),
                    ctx.player.getWalletData().getWalletUUID());

            BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                            ctx.betRequest.getBetId().equals(payload.getBetId());

            ctx.betEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var payload = ctx.betEvent.getPayload();
            assertAll("Проверка полей события ставки",
                    () -> assertNotNull(payload.getUuid(), "nats.bet.uuid"),
                    () -> assertEquals(ctx.betRequest.getType(), payload.getType(), "nats.bet.type"),
                    () -> assertEquals(ctx.betRequest.getBetId(), payload.getBetId(), "nats.bet.bet_id"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequest.getSumm()).negate().compareTo(payload.getAmount()), "nats.bet.amount"),
                    () -> assertNotNull(payload.getRawAmount(), "nats.bet.raw_amount"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequest.getSumm()).compareTo(payload.getRawAmount()), "nats.bet.raw_amount"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequest.getTotalCoef()).compareTo(payload.getTotalCoeff()), "nats.bet.total_coeff"),
                    () -> assertTrue(Math.abs(ctx.betRequest.getTime() - payload.getTime()) <= 10, "nats.bet.time"),
                    () -> assertNotNull(payload.getCreatedAt(), "nats.bet.created_at")
            );

            var wagerInfoList = payload.getWageredDepositInfo();
            assertFalse(wagerInfoList.isEmpty(), "nats.bet.wagered_deposit_info.not_empty");
            @SuppressWarnings("unchecked")
            Map<String, Object> wagerInfo = (Map<String, Object>) wagerInfoList.get(0);
            assertAll("Проверка wagered_deposit_info",
                    () -> assertEquals(ctx.depositEvent.getPayload().getUuid(), wagerInfo.get("deposit_uuid"), "nats.bet.wagered_deposit_info.deposit_uuid"),
                    () -> assertEquals(0, betAmount.compareTo(new BigDecimal((String) wagerInfo.get("updated_wagered_amount"))), "nats.bet.wagered_deposit_info.updated_wagered_amount")
            );
        });

        step("Manager API: Получение выигрыша", () -> {
            ctx.betRequest.setSumm(winAmount.toPlainString());
            ctx.betRequest.setType(NatsBettingTransactionOperation.WIN);
            var response = managerClient.makePayment(ctx.betRequest);

            assertAll("Проверка ответа выигрыша",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success")
            );
        });

        step("NATS: Проверка события won_from_iframe", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().getPlayerUUID(),
                    ctx.player.getWalletData().getWalletUUID());

            BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.WON_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                            ctx.betRequest.getBetId().equals(payload.getBetId());

            ctx.winEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var payload = ctx.winEvent.getPayload();
            assertAll("Проверка полей события выигрыша",
                    () -> assertNotNull(payload.getUuid(), "nats.win.uuid"),
                    () -> assertEquals(ctx.betRequest.getType(), payload.getType(), "nats.win.type"),
                    () -> assertEquals(ctx.betRequest.getBetId(), payload.getBetId(), "nats.win.bet_id"),
                    () -> assertEquals(0, winAmount.compareTo(payload.getAmount()), "nats.win.amount"),
                    () -> assertNotNull(payload.getRawAmount(), "nats.win.raw_amount"),
                    () -> assertEquals(0, winAmount.compareTo(payload.getRawAmount()), "nats.win.raw_amount"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequest.getTotalCoef()).compareTo(payload.getTotalCoeff()), "nats.win.total_coeff"),
                    () -> assertTrue(Math.abs(ctx.betRequest.getTime() - payload.getTime()) <= 10, "nats.win.time"),
                    () -> assertNotNull(payload.getCreatedAt(), "nats.win.created_at")
            );

            assertTrue(payload.getWageredDepositInfo().isEmpty(), "nats.win.wagered_deposit_info.empty");
        });

        step("Redis(Wallet): Проверка агрегата кошелька после выигрыша", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.player.getWalletData().getWalletUUID(),
                    (int) ctx.winEvent.getSequence());

            var depositData = aggregate.getDeposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                    .findFirst().orElse(null);

            var betRecord = aggregate.getIFrameRecords().get(0);
            var winRecord = aggregate.getIFrameRecords().get(1);

            assertAll("Проверка агрегата",
                    () -> assertEquals((int) ctx.winEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertNotNull(depositData, "redis.wallet.deposit_not_found"),
                    () -> assertEquals(0, depositAmount.compareTo(depositData.getAmount()), "redis.wallet.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), depositData.getStatus(), "redis.wallet.deposit.status"),
                    () -> assertEquals(0, betAmount.compareTo(depositData.getWageringAmount()), "redis.wallet.deposit.wagering_amount"),
                    () -> assertEquals(ctx.betEvent.getPayload().getUuid(), betRecord.getUuid(), "redis.wallet.bet.uuid"),
                    () -> assertEquals(IFrameRecordType.BET, betRecord.getType(), "redis.wallet.bet.type"),
                    () -> assertEquals(ctx.winEvent.getPayload().getUuid(), winRecord.getUuid(), "redis.wallet.win.uuid"),
                    () -> assertEquals(IFrameRecordType.WIN, winRecord.getType(), "redis.wallet.win.type")
            );
        });
    }
}
