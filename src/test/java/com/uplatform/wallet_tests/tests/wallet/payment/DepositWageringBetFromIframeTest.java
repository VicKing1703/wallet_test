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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет отыгрыш депозита при ставке на спорт через эндпоинт makePayment.
 *
 * Тест выполняет полную регистрацию игрока, делает депозит и затем ставку через
 * Manager API. В событии {@code betted_from_iframe} проверяется блок
 * {@code wagered_deposit_info}. После ставки баланс и данные депозита в Redis
 * обновляются согласно ожидаемым значениям.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> полная регистрация с KYC.</li>
 *   <li><b>Депозит:</b> вызов FAPI эндпоинта deposit.</li>
 *   <li><b>Ставка:</b> отправка запроса makePayment.</li>
 *   <li><b>Проверка NATS:</b> события deposited_money и betted_from_iframe с блоком wagered_deposit_info.</li>
 *   <li><b>Проверка Redis:</b> депозит содержит обновлённый WageringAmount и корректный баланс.</li>
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
class DepositWageringBetFromIframeTest extends BaseParameterizedTest {

    private static final BigDecimal depositAmount = new BigDecimal("150.00");

    static Stream<Arguments> betAmountProvider() {
        return Stream.of(
                Arguments.of(generateBigDecimalAmount(depositAmount)),
                Arguments.of(BigDecimal.ZERO),
                Arguments.of(depositAmount)
        );
    }

    @ParameterizedTest(name = "Отыгрыш депозита: сумма = {0}")
    @MethodSource("betAmountProvider")
    @DisplayName("Отыгрыш депозита при ставке на спорт")
    void shouldDepositAndBetFromIframeAndCheckRedis(BigDecimal betAmount) {
        final String nodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final class TestData {
            RegisteredPlayerData player;
            DepositRequestBody depositRequest;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            MakePaymentData betInput;
            MakePaymentRequest betRequest;
            NatsMessage<NatsBettingEventPayload> betEvent;
            BigDecimal expectedBalance;
        }
        final TestData ctx = new TestData();
        ctx.expectedBalance = depositAmount.subtract(betAmount);

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

        step("Redis(Wallet): Проверка агрегата кошелька после ставки", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.player.getWalletData().getWalletUUID(),
                    (int) ctx.betEvent.getSequence());

            var depositData = aggregate.getDeposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                    .findFirst().orElse(null);
            var iframeRecord = aggregate.getIFrameRecords().get(0);

            assertAll("Проверка агрегата",
                    () -> assertEquals((int) ctx.betEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertNotNull(depositData, "redis.wallet.deposit_not_found"),
                    () -> assertEquals(0, depositAmount.compareTo(depositData.getAmount()), "redis.wallet.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), depositData.getStatus(), "redis.wallet.deposit.status"),
                    () -> assertEquals(0, betAmount.compareTo(depositData.getWageringAmount()), "redis.wallet.deposit.wagering_amount"),
                    () -> assertEquals(ctx.betEvent.getPayload().getUuid(), iframeRecord.getUuid(), "redis.wallet.iframe.uuid"),
                    () -> assertEquals(ctx.betEvent.getPayload().getBetId(), iframeRecord.getBetID(), "redis.wallet.iframe.bet_id"),
                    () -> assertEquals(0, ctx.betEvent.getPayload().getTotalCoeff().compareTo(iframeRecord.getTotalCoeff()), "redis.wallet.iframe.total_coeff"),
                    () -> assertEquals(0, ctx.betEvent.getPayload().getAmount().compareTo(iframeRecord.getAmount()), "redis.wallet.iframe.amount"),
                    () -> assertNotNull(iframeRecord.getTime(), "redis.wallet.iframe.time"),
                    () -> assertNotNull(iframeRecord.getCreatedAt(), "redis.wallet.iframe.created_at"),
                    () -> assertEquals(IFrameRecordType.BET, iframeRecord.getType(), "redis.wallet.iframe.type")
            );
        });
    }
}
