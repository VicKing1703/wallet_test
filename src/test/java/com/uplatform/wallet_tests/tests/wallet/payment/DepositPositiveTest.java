package com.uplatform.wallet_tests.tests.wallet.payment;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsDepositStatus;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.kafka.dto.PaymentTransactionMessage;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;

import static io.qameta.allure.Allure.step;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * Этот тест покрывает полный E2E-поток для депозита,
 * гарантируя, что все компоненты системы корректно взаимодействуют друг с другом.
 *
 * <h3>Сценарий: Успешный депозит игрока</h3>
 * <p>В этом сценарии проверяется, что система корректно обрабатывает запрос на пополнение счета от верифицированного игрока.</p>
 * <b>GIVEN:</b>
 * <ul><li>Существует новый, зарегистрированный и верифицированный игрок.</li></ul>
 * <b>WHEN:</b>
 * <ul><li>Игрок инициирует депозит через front_api.</li></ul>
 * <b>THEN:</b>
 * <ul>
 *   <li><b>front_api</b>: Отвечает статусом <code>201 Created</code> на запрос <code>/_front_api/api/v2/payment/deposit</code>.</li>
 *   <li><b>payment-service</b>: Отправляет сообщение о транзакции в Kafka (топик <code>payment.v1.transaction</code>).</li>
 *   <li><b>wallet-manager</b>: Обрабатывает депозит и отправляет событие <code>deposited_money</code> в NATS.</li>
 *   <li><b>wallet_player_threshold_deposit_projections</b>: Обновляет порог депозитов для сервиса тегирования (таблица <code>player_threshold_deposit</code>).</li>
 *   <li><b>wallet_projections_nats_to_kafka</b>: Пересылает событие из NATS в Kafka для `report-service` (топик <code>wallet.v8.projectionSource</code>).</li>
 *   <li><b>wallet_wallet_redis</b>: Обновляет агрегат кошелька в Redis (ключ <code>wallet:{uuid}</code>).</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Deposit")
@Tag("Wallet") @Tag("Payment")
class DepositPositiveTest extends BaseTest {

    @Test
    @DisplayName("Основной E2E сценарий депозита")
    void shouldDepositAfterFullRegistration() {

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            DepositRequestBody depositRequest;
            PaymentTransactionMessage paymentTransactionMessage;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
        }
        final TestData ctx = new TestData();

        step("GIVEN: Существует новый, зарегистрированный и верифицированный игрок", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayerWithKyc();

            assertNotNull(ctx.registeredPlayer, "given.player.is_not_null");
        });

        step("WHEN: Игрок инициирует депозит через front_api", () -> {
            var amount = generateBigDecimalAmount(new BigDecimal("250"));
            ctx.depositRequest = DepositRequestBody.builder()
                    .amount(amount.toPlainString())
                    .paymentMethodId(PaymentMethodId.FAKE)
                    .currency(ctx.registeredPlayer.getWalletData().currency())
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

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "front_api.response.status_code");
        });

        step("THEN: payment-service отправляет сообщение о транзакции в Kafka", () -> {
            ctx.paymentTransactionMessage = kafkaClient.expect(PaymentTransactionMessage.class)
                    .with("playerId", ctx.registeredPlayer.getWalletData().playerUUID())
                    .with("nodeId", configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .fetch();

            assertNotNull(ctx.paymentTransactionMessage, "kafka.payment_transaction.message.not_null");
        });

        step("THEN: wallet-manager обрабатывает депозит и отправляет событие в NATS", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());
            ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                    .from(subject)
                    .withType(NatsEventType.DEPOSITED_MONEY.getHeaderValue())
                    .with("$.uuid", ctx.paymentTransactionMessage.transaction().transactionId())
                    .fetch();

            var actualPayload = ctx.depositEvent.getPayload();
            assertAll("Проверка полей события 'deposited_money' в NATS",
                    () -> assertEquals(ctx.paymentTransactionMessage.transaction().transactionId(), actualPayload.getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(ctx.depositRequest.getCurrency(), actualPayload.getCurrencyCode(), "nats.payload.currencyCode"),
                    () -> assertEquals(0, new BigDecimal(ctx.depositRequest.getAmount()).compareTo(actualPayload.getAmount()), "nats.payload.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS, actualPayload.getStatus(), "nats.payload.status"),
                    () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(), actualPayload.getNodeUuid(), "nats.payload.nodeUuid"),
                    () -> assertEquals("", actualPayload.getBonusId(), "nats.payload.bonusId")
            );
        });

        step("THEN: wallet_player_threshold_deposit_projections обновляет порог депозитов", () -> {
            var threshold = walletDatabaseClient.findDepositThresholdByPlayerUuidOrFail(
                    ctx.registeredPlayer.getWalletData().playerUUID());

            assertAll("Проверка записи в таблице 'player_threshold_deposit'",
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().playerUUID(), threshold.getPlayerUuid(), "db.player_threshold_deposit.playerUuid"),
                    () -> assertEquals(0, new BigDecimal(ctx.depositRequest.getAmount()).compareTo(threshold.getAmount()), "db.player_threshold_deposit.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.player_threshold_deposit.updatedAt")
            );
        });

        step("THEN: wallet_projections_nats_to_kafka пересылает событие из NATS в Kafka", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.depositEvent.getSequence())
                    .fetch();

            assertTrue(utils.areEquivalent(kafkaMessage, ctx.depositEvent), "kafka.wallet_projection.equivalence_with_nats");
        });

        step("THEN: wallet_wallet_redis обновляет агрегат кошелька в Redis", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.depositEvent.getSequence())
                    .fetch();

            var deposit = aggregate.deposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.paymentTransactionMessage.transaction().transactionId()))
                    .findFirst().orElse(null);

            assertAll("Проверка данных депозита в агрегате кошелька Redis",
                    () -> assertNotNull(deposit, "redis.wallet_aggregate.deposit.not_null"),
                    () -> assertEquals((int) ctx.depositEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet_aggregate.lastSeqNumber"),
                    () -> assertEquals(0, new BigDecimal(ctx.depositRequest.getAmount()).compareTo(deposit.amount()), "redis.wallet_aggregate.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), deposit.status(), "redis.wallet_aggregate.deposit.status"),
                    () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(), deposit.nodeUUID(), "redis.wallet_aggregate.deposit.nodeUUID"),
                    () -> assertEquals("", deposit.bonusID(), "redis.wallet_aggregate.deposit.bonusID"),
                    () -> assertEquals(ctx.depositRequest.getCurrency(), deposit.currencyCode(), "redis.wallet_aggregate.deposit.currencyCode"),
                    () -> assertEquals(0, deposit.wageringAmount().compareTo(BigDecimal.ZERO), "redis.wallet_aggregate.deposit.wageringAmount")
            );
        });
    }
}
