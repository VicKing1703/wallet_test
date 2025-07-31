package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.api.kafka.dto.PaymentTransactionMessage;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.WithdrawalRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.WithdrawalRedirect;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsBlockAmountEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка заявки на вывод средств после полной регистрации.
 *
 * Тест выполняет полную регистрацию пользователя, затем увеличивает баланс
 * через CAP и инициирует заявку на вывод средств. Проверяется, что публикация
 * события block_amount_started появляется в NATS и Kafka, а в Redis создаётся
 * соответствующая блокировка суммы.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> выполнение шага полной регистрации с KYC.</li>
 *   <li><b>Основное действие:</b> корректировка баланса и вызов FAPI эндпоинта вывода.</li>
 *   <li><b>Проверка ответа API:</b> статус HTTP 201.</li>
 *   <li><b>Kafka:</b> получение transactionId из payment.v1.transaction.</li>
 *   <li><b>Проверка NATS:</b> событие block_amount_started.</li>
 *   <li><b>Проверка Kafka:</b> сообщение в топике wallet.v8.projectionSource.</li>
 *   <li><b>Проверка Redis:</b> создание блокировки средств.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: FAPI, CAP</li>
 *   <li>NATS</li>
 *   <li>Kafka</li>
 *   <li>Redis</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.fapi.client.FapiClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Payment")
@Feature("Withdrawal")
@Suite("Позитивные сценарии: Withdrawal")
@Tag("Wallet") @Tag("Payment")
class WithdrawalPositiveTest extends BaseTest {

    @Test
    @DisplayName("Полная регистрация и вывод средств")
    void shouldWithdrawAfterBalanceAdjustment() throws Exception {
        final String nodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final BigDecimal adjustmentAmount = new BigDecimal("20");
        final BigDecimal withdrawalAmount = new BigDecimal("10");

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            WithdrawalRequestBody withdrawalRequest;
            String transactionId;
            NatsMessage<NatsBlockAmountEventPayload> blockEvent;
        }
        final TestData ctx = new TestData();

        step("Default Step: Полная регистрация игрока с KYC", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayerWithKyc();
            assertNotNull(ctx.registeredPlayer, "default_step.registration_with_kyc");
        });

        step("CAP API: Корректировка баланса", () -> {
            var request = CreateBalanceAdjustmentRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .amount(adjustmentAmount)
                    .reason(ReasonType.MALFUNCTION)
                    .operationType(OperationType.CORRECTION)
                    .direction(DirectionType.INCREASE)
                    .comment("")
                    .build();

            var response = capAdminClient.createBalanceAdjustment(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    nodeId,
                    "6dfe249e-e967-477b-8a42-83efe85c7c3a",
                    request);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.create_balance_adjustment.status_code");
        });

        step("FAPI: Заявка на вывод средств", () -> {
            ctx.withdrawalRequest = WithdrawalRequestBody.builder()
                    .amount(withdrawalAmount.toPlainString())
                    .paymentMethodId(PaymentMethodId.MOCK)
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .country(configProvider.getEnvironmentConfig().getPlatform().getCountry())
                    .context(Collections.emptyMap())
                    .redirect(WithdrawalRequestBody.RedirectUrls.builder()
                            .failed(WithdrawalRedirect.FAILED.url())
                            .success(WithdrawalRedirect.SUCCESS.url())
                            .pending(WithdrawalRedirect.PENDING.url())
                            .build())
                    .build();

            var response = publicClient.withdrawal(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.withdrawalRequest);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.withdrawal.status_code");
        });

        step("Kafka: Получение transactionId", () -> {
            var paymentMessage = kafkaClient.expect(PaymentTransactionMessage.class)
                    .with("playerId", ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .with("nodeId", nodeId)
                    .fetch();

            ctx.transactionId = paymentMessage.getTransaction().getTransactionId();
            assertNotNull(ctx.transactionId, "kafka.transaction.id");
        });

        step("NATS: Проверка события block_amount_started", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsBlockAmountEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BLOCK_AMOUNT_STARTED.getHeaderValue().equals(typeHeader)
                            && ctx.transactionId.equals(payload.getUuid());

            ctx.blockEvent = natsClient.expect(NatsBlockAmountEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var payload = ctx.blockEvent.getPayload();
            assertAll("Проверка основных полей NATS payload",
                    () -> assertEquals(ctx.transactionId, payload.getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(0, withdrawalAmount.negate().compareTo(payload.getAmount()), "nats.payload.amount"),
                    () -> assertEquals("", payload.getReason(), "nats.payload.reason"),
                    () -> assertEquals("00000000-0000-0000-0000-000000000000", payload.getUserUuid(), "nats.payload.user_uuid"),
                    () -> assertEquals("", payload.getUserName(), "nats.payload.user_name"),
                    () -> assertNotNull(payload.getCreatedAt(), "nats.payload.created_at"),
                    () -> assertNotNull(payload.getExpiredAt(), "nats.payload.expired_at")
            );
        });

        step("Kafka: Проверка поступления сообщения block_amount_started в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.blockEvent.getSequence())
                    .fetch();

            assertTrue(utils.areEquivalent(kafkaMessage, ctx.blockEvent), "kafka.payload");
        });

        step("Redis(Wallet): Проверка данных кошелька", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.blockEvent.getSequence());

            var blockedAmountInfo = aggregate.getBlockedAmounts().get(0);
            var expectedBalance = adjustmentAmount.subtract(withdrawalAmount);

            assertAll("Проверка агрегата после BlockAmount",
                    () -> assertEquals((int) ctx.blockEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, expectedBalance.compareTo(aggregate.getBalance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, expectedBalance.compareTo(aggregate.getAvailableWithdrawalBalance()), "redis.aggregate.available_withdrawal_balance"),
                    () -> assertEquals(0, adjustmentAmount.compareTo(aggregate.getBalanceBefore()), "redis.aggregate.balance_before"),
                    () -> assertEquals(ctx.transactionId, blockedAmountInfo.getUuid(), "redis.aggregate.blocked_amount.uuid"),
                    () -> assertEquals("00000000-0000-0000-0000-000000000000", blockedAmountInfo.getUserUUID(), "redis.aggregate.blocked_amount.user_uuid"),
                    () -> assertEquals("", blockedAmountInfo.getUserName(), "redis.aggregate.blocked_amount.user_name"),
                    () -> assertEquals(0, withdrawalAmount.negate().compareTo(blockedAmountInfo.getAmount()), "redis.aggregate.blocked_amount.amount"),
                    () -> assertEquals(0, withdrawalAmount.compareTo(blockedAmountInfo.getDeltaAvailableWithdrawalBalance()), "redis.aggregate.blocked_amount.delta_available_withdrawal_balance"),
                    () -> assertEquals("", blockedAmountInfo.getReason(), "redis.aggregate.blocked_amount.reason"),
                    () -> assertNotNull(blockedAmountInfo.getCreatedAt(), "redis.aggregate.blocked_amount.created_at"),
                    () -> assertNotNull(blockedAmountInfo.getExpiredAt(), "redis.aggregate.blocked_amount.expired_at")
            );
        });
    }
}
