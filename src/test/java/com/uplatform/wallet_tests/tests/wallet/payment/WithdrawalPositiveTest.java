package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.WithdrawalRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.WithdrawalRedirect;
import com.uplatform.wallet_tests.api.kafka.dto.PaymentTransactionMessage;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsBlockAmountEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.base.BaseTest;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * Этот тест проверяет полный E2E-сценарий создания заявки на вывод средств игроком.
 *
 * <h3>Сценарий: Успешная заявка на вывод средств</h3>
 * <p>Проверяется, что после пополнения баланса и создания заявки на вывод через FAPI
 * система корректно блокирует средства на счете игрока и отправляет соответствующие
 * события в NATS и Kafka.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует зарегистрированный и верифицированный игрок.</li>
 *   <li>Баланс игрока пополнен на необходимую для вывода сумму через CAP.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Игрок создает заявку на вывод средств через FAPI.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>fapi</b>: Отвечает статусом <code>201 Created</code>.</li>
 *   <li><b>wallet-payment</b>: Отправляет сообщение о создании транзакции в Kafka-топик `payment.v1.transaction`.</li>
 *   <li><b>wallet-manager</b>: Отправляет событие <code>block_amount_started</code> в NATS.</li>
 *   <li><b>wallet-manager</b>: Отправляет сообщение для проекции в Kafka-топик `wallet.v8.projectionSource`.</li>
 *   <li><b>wallet_wallet_redis</b>: В агрегате кошелька создается блокировка на сумму вывода, а баланс уменьшается.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Payment")
@Feature("Withdrawal")
@Suite("Позитивные сценарии: Withdrawal")
@Tag("Wallet") @Tag("Payment")
class WithdrawalPositiveTest extends BaseTest {

    private static final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("20.00");
    private static final BigDecimal WITHDRAWAL_AMOUNT = new BigDecimal("10.00");

    @Test
    @DisplayName("Создание заявки на вывод средств после пополнения баланса")
    void shouldCreateWithdrawalRequestAndBlockAmount() throws Exception {

        final class TestData {
            RegisteredPlayerData player;
            String transactionId;
            NatsMessage<NatsBlockAmountEventPayload> blockEvent;

            BigDecimal expectedBalanceAfterBlock;
        }
        final TestData ctx = new TestData();

        step("GIVEN: Зарегистрированный игрок с пополненным балансом", () -> {
            step("Регистрация нового игрока с KYC", () -> {
                ctx.player = defaultTestSteps.registerNewPlayerWithKyc();
                assertNotNull(ctx.player, "setup.player.creation");
            });

            step("Пополнение баланса через CAP", () -> {
                var request = CreateBalanceAdjustmentRequest.builder()
                        .currency(ctx.player.walletData().currency())
                        .amount(ADJUSTMENT_AMOUNT)
                        .reason(ReasonType.MALFUNCTION)
                        .operationType(OperationType.CORRECTION)
                        .direction(DirectionType.INCREASE)
                        .comment("Test balance adjustment")
                        .build();

                var response = capAdminClient.createBalanceAdjustment(
                        ctx.player.walletData().playerUUID(),
                        utils.getAuthorizationHeader(),
                        configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                        "6dfe249e-e967-477b-8a42-83efe85c7c3a", // idempotency-key
                        request);

                assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.create_balance_adjustment.status_code");
            });

            step("Подготовка ожидаемых результатов", () -> {
                ctx.expectedBalanceAfterBlock = ADJUSTMENT_AMOUNT.subtract(WITHDRAWAL_AMOUNT);
            });
        });

        step("WHEN: Игрок создает заявку на вывод средств", () -> {
            var withdrawalRequest = WithdrawalRequestBody.builder()
                    .amount(WITHDRAWAL_AMOUNT.toPlainString())
                    .paymentMethodId(PaymentMethodId.MOCK)
                    .currency(ctx.player.walletData().currency())
                    .country(configProvider.getEnvironmentConfig().getPlatform().getCountry())
                    .context(Collections.emptyMap())
                    .redirect(WithdrawalRequestBody.RedirectUrls.builder()
                            .failed(WithdrawalRedirect.FAILED.url())
                            .success(WithdrawalRedirect.SUCCESS.url())
                            .pending(WithdrawalRedirect.PENDING.url())
                            .build())
                    .build();

            var response = publicClient.withdrawal(
                    ctx.player.authorizationResponse().getBody().getToken(),
                    withdrawalRequest);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.withdrawal.status_code");

            step("Получение transactionId из Kafka", () -> {
                var paymentMessage = kafkaClient.expect(PaymentTransactionMessage.class)
                        .with("playerId", ctx.player.walletData().playerUUID())
                        .fetch();

                ctx.transactionId = paymentMessage.transaction().transactionId();
                assertNotNull(ctx.transactionId, "kafka.payment_transaction.id_not_found");
            });
        });

        step("THEN: wallet-manager отправляет событие `block_amount_started` в NATS", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.walletData().playerUUID(),
                    ctx.player.walletData().walletUUID());

            ctx.blockEvent = natsClient.expect(NatsBlockAmountEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BLOCK_AMOUNT_STARTED.getHeaderValue())
                    .with("$.uuid", ctx.transactionId)
                    .fetch();

            var payload = ctx.blockEvent.getPayload();
            assertAll("Проверка полей события block_amount_started в NATS",
                    () -> assertEquals(ctx.transactionId, payload.uuid(), "nats.payload.uuid"),
                    () -> assertEquals(0, WITHDRAWAL_AMOUNT.negate().compareTo(payload.amount()), "nats.payload.amount")
            );
        });

        step("THEN: wallet-manager отправляет сообщение в Kafka-топик проекции", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.blockEvent.getSequence())
                    .fetch();

            assertTrue(utils.areEquivalent(kafkaMessage, ctx.blockEvent), "kafka.projection.payload_mismatch");
        });

        step("THEN: wallet_wallet_redis обновляет баланс и создает блокировку", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.player.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.blockEvent.getSequence())
                    .fetch();

            var blockedAmountInfo = aggregate.blockedAmounts().stream()
                    .filter(b -> b.uuid().equals(ctx.transactionId))
                    .findFirst().orElse(null);

            assertAll("Проверка агрегата кошелька в Redis после блокировки",
                    () -> assertEquals((int) ctx.blockEvent.getSequence(), aggregate.lastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterBlock.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertNotNull(blockedAmountInfo, "redis.aggregate.blocked_amount.not_found"),
                    () -> assertEquals(ctx.transactionId, blockedAmountInfo.uuid(), "redis.aggregate.blocked_amount.uuid"),
                    () -> assertEquals(0, WITHDRAWAL_AMOUNT.negate().compareTo(blockedAmountInfo.amount()), "redis.aggregate.blocked_amount.amount")
            );
        });
    }
}
