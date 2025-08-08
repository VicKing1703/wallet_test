package com.uplatform.wallet_tests.tests.wallet.limit.deposit;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.deposit.SetDepositLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.kafka.dto.PaymentTransactionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsDepositStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверка уменьшения остатка лимита Deposit после совершения депозита.
 *
 * <p>Тест создает лимит на депозит для разных периодов и выполняет депозит,
 * затем проверяет, что в агрегате кошелька в Redis лимит обновил поля
 * {@code spent} и {@code rest} соответственно сумме депозита.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> полная регистрация с верификацией.</li>
 *   <li><b>Установка лимита:</b> создание DepositLimit через Public API.</li>
 *   <li><b>Проверка NATS:</b> событие {@code limit_changed_v2}.</li>
 *   <li><b>Основное действие:</b> депозит через Public API.</li>
 *   <li><b>Kafka:</b> получение transactionId из payment.v1.transaction.</li>
 *   <li><b>Проверка NATS:</b> событие {@code deposited_money}.</li>
 *   <li><b>Проверка Redis:</b> остаток и потраченная сумма лимита изменились.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка лимита и выполнение депозита.</li>
 *   <li>NATS: события limit_changed_v2 и deposited_money.</li>
 *   <li>Kafka: payment.v1.transaction.</li>
 *   <li>Redis: агрегат кошелька.</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.fapi.client.FapiClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("DepositLimit")
@Suite("Позитивные сценарии: DepositLimit")
@Tag("Limits") @Tag("Wallet") @Tag("Payment")
public class DepositLimitRestAfterDepositParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal limitAmount = new BigDecimal("100.15");
    private static final BigDecimal depositSmallAmount = new BigDecimal("10.00");

    static Stream<Arguments> periodAndAmountProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY, depositSmallAmount),
                arguments(NatsLimitIntervalType.WEEKLY, depositSmallAmount),
                arguments(NatsLimitIntervalType.MONTHLY, depositSmallAmount),
                arguments(NatsLimitIntervalType.DAILY, limitAmount),
                arguments(NatsLimitIntervalType.WEEKLY, limitAmount),
                arguments(NatsLimitIntervalType.MONTHLY, limitAmount)
        );
    }


    @ParameterizedTest(name = "период = {0}, сумма депозита = {1}")
    @MethodSource("periodAndAmountProvider")
    @DisplayName("Изменение остатка DepositLimit после депозита")
    void depositShouldDecreaseLimitRest(NatsLimitIntervalType periodType, BigDecimal depositAmount) throws Exception {
        final String nodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            SetDepositLimitRequest limitRequest;
            NatsMessage<NatsLimitChangedV2Payload> limitEvent;
            DepositRequestBody depositRequest;
            PaymentTransactionMessage kafkaPaymentMessage;
            String transactionId;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            BigDecimal expectedRest;
        }
        final TestData ctx = new TestData();

        step("Default Step: Полная регистрация игрока", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayerWithKyc();

            assertNotNull(ctx.registeredPlayer, "default_step.registration_with_kyc");
        });

        step("Public API: Установка лимита на депозит", () -> {
            ctx.limitRequest = SetDepositLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .type(periodType)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setDepositLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.limitRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_deposit_limit.status_code");
        });

        step("NATS: получение события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, header) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(header) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            NatsLimitType.DEPOSIT.getValue().equals(payload.getLimits().get(0).getLimitType()) &&
                            payload.getLimits().get(0).getAmount() != null &&
                            limitAmount.compareTo(payload.getLimits().get(0).getAmount()) == 0;

            ctx.limitEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            assertNotNull(ctx.limitEvent, "nats.limit_changed_v2_event.message_not_null");
        });

        step("Public API: Выполнение депозита", () -> {
            ctx.expectedRest = limitAmount.subtract(depositAmount);

            ctx.depositRequest = DepositRequestBody.builder()
                    .amount(depositAmount.toPlainString())
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
                    ctx.depositRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.deposit.status_code");
        });

        step("Kafka: Получение transactionId", () -> {
            ctx.kafkaPaymentMessage = kafkaClient.expect(PaymentTransactionMessage.class)
                    .with("playerId", ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .with("nodeId", nodeId)
                    .fetch();

            ctx.transactionId = ctx.kafkaPaymentMessage.getTransaction().getTransactionId();

            assertNotNull(ctx.transactionId, "kafka.transaction.id");
        });

        step("NATS: Проверка события deposited_money", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsDepositedMoneyPayload, String> filter = (payload, header) ->
                    NatsEventType.DEPOSITED_MONEY.getHeaderValue().equals(header);

            ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            assertNotNull(ctx.depositEvent, "nats.deposited_money_event.message_not_null");

            assertAll("nats.deposited_money_event.payload_validation",
                    () -> assertEquals(ctx.transactionId, ctx.depositEvent.getPayload().getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(ctx.depositRequest.getCurrency(), ctx.depositEvent.getPayload().getCurrencyCode(), "nats.payload.currency_code"),
                    () -> assertEquals(0, depositAmount.compareTo(ctx.depositEvent.getPayload().getAmount()), "nats.payload.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS, ctx.depositEvent.getPayload().getStatus(), "nats.payload.status"),
                    () -> assertEquals(nodeId, ctx.depositEvent.getPayload().getNodeUuid(), "nats.payload.node_uuid"),
                    () -> assertEquals("", ctx.depositEvent.getPayload().getBonusId(), "nats.payload.bonus_id")
            );
        });

        step("Redis(Wallet): Проверка изменений лимита", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.depositEvent.getSequence());

            var redisLimit = aggregate.getLimits().stream()
                    .filter(l -> NatsLimitType.DEPOSIT.getValue().equals(l.getLimitType()) &&
                            ctx.limitEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getExternalID()))
                    .findFirst().orElse(null);

            assertNotNull(redisLimit, "redis.wallet.limit_found");

            assertAll("redis.wallet.limit_content_validation",
                    () -> assertEquals(0, limitAmount.compareTo(redisLimit.getAmount()), "redis.wallet.limit.amount"),
                    () -> assertEquals(0, depositAmount.compareTo(redisLimit.getSpent()), "redis.wallet.limit.spent"),
                    () -> assertEquals(0, ctx.expectedRest.compareTo(redisLimit.getRest()), "redis.wallet.limit.rest")
            );
        });
    }
}
