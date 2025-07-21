package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsDepositStatus;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
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
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка успешного депозита после полной регистрации игрока.
 *
 * <p>Тест выполняет полную регистрацию с верификацией и затем инициирует депозит
 * через FAPI. Проверяется корректность поступления события в NATS, запись в БД,
 * отправка сообщения в Kafka и обновление агрегата кошелька в Redis.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> выполнение шага полной регистрации с KYC.</li>
 *   <li><b>Основное действие:</b> вызов FAPI эндпоинта депозита.</li>
 *   <li><b>Проверка ответа API:</b> статус HTTP 201.</li>
 *   <li><b>Kafka:</b> получение transactionId из payment.v1.transaction.</li>
 *   <li><b>Проверка NATS:</b> получение события deposited_money.</li>
 *   <li><b>Проверка БД:</b> запись в таблицу player_threshold_deposit.</li>
 *   <li><b>Проверка Kafka:</b> сообщение в топике wallet.v8.projectionSource.</li>
 *   <li><b>Проверка Redis:</b> обновление агрегата кошелька.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: FAPI</li>
 *   <li>NATS</li>
 *   <li>Kafka</li>
 *   <li>База данных Wallet</li>
 *   <li>Redis</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.fapi.client.FapiClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Deposit")
@Tag("Wallet")
class DepositPositiveTest extends BaseTest {

    @Test
    @DisplayName("Полная регистрация и депозит игрока")
    void shouldDepositAfterFullRegistration() throws Exception {
        final String nodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            DepositRequestBody depositRequest;
            String transactionId;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
        }
        final TestData ctx = new TestData();

        step("Default Step: Полная регистрация игрока с KYC", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayerWithKyc();
            assertNotNull(ctx.registeredPlayer, "default_step.registration_with_kyc");
        });

        step("FAPI: Выполнение депозита", () -> {
            var amount = generateBigDecimalAmount(new BigDecimal("15"));
            ctx.depositRequest = DepositRequestBody.builder()
                    .amount(amount.toPlainString())
                    .paymentMethodId(PaymentMethodId.FAKE)
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .country(configProvider.getEnvironmentConfig().getPlatform().getCountry())
                    .redirect(DepositRequestBody.RedirectUrls.builder()
                            .failed(DepositRedirect.FAILED.url())
                            .success(DepositRedirect.SUCCESS.url())
                            .pending(DepositRedirect.PENDING.url())
                            .build())
                    .build();

            var response = publicClient.deposit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.depositRequest);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.deposit.status_code");
        });

        step("Kafka: Получение transactionId", () -> {
            var paymentMessage = paymentKafkaClient.expectTransactionMessage(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    nodeId);
            ctx.transactionId = paymentMessage.getTransaction().getTransactionId();
            assertNotNull(ctx.transactionId, "kafka.transaction.id");
        });

        step("NATS: Проверка поступления события deposited_money", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsDepositedMoneyPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.DEPOSITED_MONEY.getHeaderValue().equals(typeHeader);

            ctx.depositEvent = natsClient.findMessageAsync(
                    subject,
                    NatsDepositedMoneyPayload.class,
                    filter).get();

            var actualPayload = ctx.depositEvent.getPayload();
            assertAll("Проверка основных полей NATS payload",
                    () -> assertEquals(ctx.transactionId, actualPayload.getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(ctx.depositRequest.getCurrency(), actualPayload.getCurrencyCode(), "nats.payload.currency_code"),
                    () -> assertEquals(0, new BigDecimal(ctx.depositRequest.getAmount()).compareTo(actualPayload.getAmount()), "nats.payload.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS, actualPayload.getStatus(), "nats.payload.status"),
                    () -> assertEquals(nodeId, actualPayload.getNodeUuid(), "nats.payload.node_uuid"),
                    () -> assertEquals("", actualPayload.getBonusId(), "nats.payload.bonus_id")
            );
        });

        step("DB Wallet: Проверка записи порога депозита", () -> {
            var threshold = walletDatabaseClient.findDepositThresholdByPlayerUuidOrFail(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID());
            assertAll("Проверка полей player_threshold_deposit",
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getPlayerUUID(), threshold.getPlayerUuid(), "db.ptd.player_uuid"),
                    () -> assertEquals(0, new BigDecimal(ctx.depositRequest.getAmount()).compareTo(threshold.getAmount()), "db.ptd.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.ptd.updated_at")
            );
        });

        step("Kafka: Проверка поступления сообщения deposited_money в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.depositEvent.getSequence());
            assertTrue(utils.areEquivalent(kafkaMessage, ctx.depositEvent), "kafka.payload");
        });

        step("Redis(Wallet): Проверка агрегата кошелька", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.depositEvent.getSequence());

            var deposit = aggregate.getDeposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.transactionId))
                    .findFirst().orElse(null);

            assertAll("Проверка данных депозита в агрегате",
                    () -> assertEquals((int) ctx.depositEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertNotNull(deposit, "redis.wallet.deposit_not_found"),
                    () -> assertEquals(0, new BigDecimal(ctx.depositRequest.getAmount()).compareTo(deposit.getAmount()), "redis.wallet.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), deposit.getStatus(), "redis.wallet.deposit.status"),
                    () -> assertEquals(nodeId, deposit.getNodeUUID(), "redis.wallet.deposit.node_uuid"),
                    () -> assertEquals("", deposit.getBonusID(), "redis.wallet.deposit.bonus_id"),
                    () -> assertEquals(ctx.depositRequest.getCurrency(), deposit.getCurrencyCode(), "redis.wallet.deposit.currency_code"),
                    () -> assertEquals(0, deposit.getWageringAmount().compareTo(deposit.getAmount()), "redis.wallet.deposit.wagering_amount")
            );
        });
    }
}
