package com.uplatform.wallet_tests.tests.wallet.limit.deposit;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.deposit.SetDepositLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.UpdateTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.kafka.dto.PaymentTransactionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверяет корректность пересчета лимита Deposit после депозита и уменьшения лимита.
 *
 * Создаётся лимит Deposit, затем выполняется депозит через Public API. После этого
 * лимит уменьшается PATCH-запросом. Значение {@code amount} принимает новую сумму.
 * Если остаток лимита после депозита меньше новой суммы, поле {@code rest} сохраняет
 * прежний остаток, а {@code spent} равняется разнице между новой суммой и остатком.
 * В противном случае и {@code amount}, и {@code rest} совпадают с новой суммой, а
 * {@code spent} обнуляется.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> полная регистрация с верификацией.</li>
 *   <li><b>Основное действие:</b> установка лимита Deposit через Public API.</li>
 *   <li><b>Основное действие:</b> выполнение депозита.</li>
 *   <li><b>Основное действие:</b> изменение лимита PATCH-запросом в меньшую сторону.</li>
 *   <li><b>Проверка NATS:</b> событие limit_changed_v2 с event_type amount_updated.</li>
 *   <li><b>Проверка Kafka:</b> сообщение Wallet Projection.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька содержит пересчитанный лимит.</li>
 *   <li><b>Проверка CAP:</b> получение лимитов игрока.</li>
 *   <li><b>Проверка Public API:</b> получение лимита Deposit.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка и изменение лимита Deposit.</li>
 *   <li>NATS: deposited_money, limit_changed_v2.</li>
 *   <li>Kafka: Wallet Projection, payment.v1.transaction.</li>
 *   <li>Redis: агрегат кошелька.</li>
 *   <li>CAP API: список лимитов игрока.</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("DepositLimit")
@Suite("Позитивные сценарии: DepositLimit")
@Tag("Limits") @Tag("Wallet") @Tag("Payment")
public class DepositLimitUpdateAfterDepositParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal limitAmountBase = new BigDecimal("150.12");
    private static final BigDecimal depositAmount = new BigDecimal("10.15");
    private static final BigDecimal newAmountLess = new BigDecimal("100.00");
    private static final BigDecimal newAmountMore = new BigDecimal("140.00");

    static Stream<Arguments> periodAndAmountProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY, newAmountLess),
                arguments(NatsLimitIntervalType.DAILY, newAmountMore),
                arguments(NatsLimitIntervalType.WEEKLY, newAmountLess),
                arguments(NatsLimitIntervalType.WEEKLY, newAmountMore),
                arguments(NatsLimitIntervalType.MONTHLY, newAmountLess),
                arguments(NatsLimitIntervalType.MONTHLY, newAmountMore)
        );
    }

    @ParameterizedTest(name = "период = {0}, новый лимит = {1}")
    @MethodSource("periodAndAmountProvider")
    @DisplayName("Пересчет DepositLimit после депозита и уменьшения лимита")
    void updateDepositLimitAfterDeposit(NatsLimitIntervalType periodType, BigDecimal newAmount) throws Exception {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            SetDepositLimitRequest createRequest;
            UpdateTurnoverLimitRequest updateRequest;
            DepositRequestBody depositRequest;
            LimitMessage kafkaLimitMessage;
            PaymentTransactionMessage kafkaPaymentMessage;
            String transactionId;
            NatsMessage<NatsLimitChangedV2Payload> createEvent;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            NatsMessage<NatsLimitChangedV2Payload> updateEvent;
        }
        final TestData ctx = new TestData();

        BigDecimal oldRest = limitAmountBase.subtract(depositAmount);
        BigDecimal expectedSpentAfterUpdate =
                oldRest.compareTo(newAmount) < 0 ? newAmount.subtract(oldRest) : BigDecimal.ZERO;
        BigDecimal expectedRestAfterUpdate =
                oldRest.compareTo(newAmount) < 0 ? oldRest : newAmount;

        step("Default Step: Полная регистрация игрока", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayerWithKyc();
            assertNotNull(ctx.registeredPlayer, "default_step.registration_with_kyc");
        });

        step("Public API: Установка лимита на депозит", () -> {
            ctx.createRequest = SetDepositLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .amount(limitAmountBase.toString())
                    .type(periodType)
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setDepositLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_deposit_limit.status_code");
        });

        step("Kafka: Получение сообщения из топика limits.v2", () -> {
            var expectedAmount = limitAmountBase.stripTrailingZeros().toPlainString();
            ctx.kafkaLimitMessage = kafkaClient.expect(LimitMessage.class)
                    .with("playerId", ctx.registeredPlayer.getWalletData().playerUUID())
                    .with("limitType", NatsLimitType.DEPOSIT.getValue())
                    .with("currencyCode", ctx.registeredPlayer.getWalletData().currency())
                    .with("amount", expectedAmount
            )
                    .fetch();
            assertNotNull(ctx.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
        });

        step("NATS: Проверка поступления события limit_changed_v2 о создании", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID()
            );

            ctx.createEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.limits[0].external_id", ctx.kafkaLimitMessage.id())
                    .fetch();

            assertNotNull(ctx.createEvent, "nats.limit_changed_v2_event.creation.message_not_null");
        });

        step("Public API: Выполнение депозита", () -> {
            ctx.depositRequest = DepositRequestBody.builder()
                    .amount(depositAmount.toPlainString())
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
                    ctx.depositRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.deposit.status_code");
        });

        step("Kafka: Получение transactionId", () -> {
            ctx.kafkaPaymentMessage = kafkaClient.expect(PaymentTransactionMessage.class)
                    .with("playerId", ctx.registeredPlayer.getWalletData().playerUUID())
                    .with("nodeId", platformNodeId)
                    .fetch();

            ctx.transactionId = ctx.kafkaPaymentMessage.transaction().transactionId();

            assertNotNull(ctx.transactionId, "kafka.transaction.id");
        });

        step("NATS: Проверка события deposited_money", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());

            ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                    .from(subject)
                    .withType(NatsEventType.DEPOSITED_MONEY.getHeaderValue())
                    .fetch();

            assertNotNull(ctx.depositEvent, "nats.deposited_money_event.message_not_null");
        });

        step("Public API: Изменение лимита на меньшую сумму", () -> {
            ctx.updateRequest = UpdateTurnoverLimitRequest.builder()
                    .amount(newAmount.toString())
                    .build();

            var response = publicClient.updateRecalculatedLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createEvent.getPayload().getLimits().get(0).getExternalId(),
                    ctx.updateRequest
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.update_recalculated_limit.status_code");
        });

        step("NATS: Проверка события limit_changed_v2 об обновлении", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID()
            );

            ctx.updateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.event_type", NatsLimitEventType.AMOUNT_UPDATED.getValue())
                    .with("$.limits[0].external_id", ctx.createEvent.getPayload().getLimits().get(0).getExternalId())
                    .fetch();

            assertNotNull(ctx.updateEvent, "nats.limit_changed_v2_event.update");

            var updLimit = ctx.updateEvent.getPayload().getLimits().get(0);
            assertAll("nats.limit_changed_v2_event.update.content_validation",
                    () -> assertEquals(NatsLimitEventType.AMOUNT_UPDATED.getValue(), ctx.updateEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.createEvent.getPayload().getLimits().get(0).getExternalId(), updLimit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.DEPOSIT.getValue(), updLimit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), updLimit.getIntervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                    () -> assertEquals(0, newAmount.compareTo(updLimit.getAmount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), updLimit.getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(updLimit.getStartedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertNotNull(updLimit.getExpiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(updLimit.getStatus(), "nats.limit_changed_v2_event.limit.status")
            );
        });

        step("Kafka Projection: Сравнение данных из NATS и Kafka Wallet Projection", () -> {
            var projectionMsg = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.updateEvent.getSequence())
                    .fetch();
            assertNotNull(projectionMsg, "kafka.wallet_projection.message_not_null");
            assertTrue(utils.areEquivalent(projectionMsg, ctx.updateEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis(Wallet): Проверка данных лимита в агрегате", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.updateEvent.getSequence())
                    .fetch();

            assertFalse(aggregate.limits().isEmpty(), "redis.wallet_aggregate.limits_list_not_empty");

            var redisLimitOpt = aggregate.limits().stream()
                    .filter(l -> ctx.updateEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getExternalID()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.deposit_limit_found");
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().getLimits().get(0).getExternalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.DEPOSIT.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), redisLimit.getIntervalType(), "redis.wallet_aggregate.limit.intervalType"),
                    () -> assertEquals(0, newAmount.compareTo(redisLimit.getAmount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, expectedSpentAfterUpdate.compareTo(redisLimit.getSpent()), "redis.wallet_aggregate.limit.spent"),
                    () -> assertEquals(0, expectedRestAfterUpdate.compareTo(redisLimit.getRest()), "redis.wallet_aggregate.limit.rest"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), redisLimit.getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(redisLimit.getStartedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertNotNull(redisLimit.getExpiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(redisLimit.isStatus(), "redis.wallet_aggregate.limit.status_is_true")
            );
        });

        step("CAP: Получение лимитов игрока и их валидация", () -> {
            var response = capAdminClient.getPlayerLimits(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code");
            assertNotNull(response.getBody(), "cap.get_player_limits.response_body_not_null");
            assertNotNull(response.getBody().getData(), "cap.get_player_limits.response_body.data_list_not_null");

            var capLimitOpt = response.getBody().getData().stream()
                    .filter(l -> newAmount.compareTo(l.getAmount()) == 0)
                    .findFirst();

            assertTrue(capLimitOpt.isPresent(), "cap.get_player_limits.limit_not_found");
            var capLimit = capLimitOpt.get();

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertTrue(capLimit.isStatus(), "cap.get_player_limits.limit.status_is_true"),
                    () -> assertEquals(periodType.getValue(), capLimit.getPeriod().toString().toLowerCase(), "cap.get_player_limits.limit.intervalType"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), capLimit.getCurrency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, newAmount.compareTo(capLimit.getAmount()), "cap.get_player_limits.limit.amount"),
                    () -> assertEquals(0, expectedRestAfterUpdate.compareTo(capLimit.getRest()), "cap.get_player_limits.limit.rest"),
                    () -> {
                        if (capLimit.getSpent() != null) {
                            assertEquals(0, expectedSpentAfterUpdate.compareTo(capLimit.getSpent()),
                                    "cap.get_player_limits.limit.spent");
                        }
                    },
                    () -> assertNotNull(capLimit.getCreatedAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(capLimit.getDeactivatedAt(), "cap.get_player_limits.limit.deactivatedAt"),
                    () -> assertNotNull(capLimit.getStartedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNotNull(capLimit.getExpiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение лимита Deposit и его валидация", () -> {
            var response = publicClient.getDepositLimits(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_deposit_limits.status_code");
            assertFalse(response.getBody().isEmpty(), "fapi.get_deposit_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> ctx.updateEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getId()))
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_deposit_limits.limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_deposit_limits.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().getLimits().get(0).getExternalId(), fapiLimit.getId(), "fapi.get_deposit_limits.limit.id"),
                    () -> assertEquals(periodType.getValue(), fapiLimit.getType(), "fapi.get_deposit_limits.limit.type"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), fapiLimit.getCurrency(), "fapi.get_deposit_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.isStatus(), "fapi.get_deposit_limits.limit.status_is_true"),
                    () -> assertEquals(0, newAmount.compareTo(fapiLimit.getAmount()), "fapi.get_deposit_limits.limit.amount"),
                    () -> assertEquals(0, expectedRestAfterUpdate.compareTo(fapiLimit.getRest()), "fapi.get_deposit_limits.limit.rest"),
                    () -> assertEquals(0, expectedSpentAfterUpdate.compareTo(fapiLimit.getSpent()), "fapi.get_deposit_limits.limit.spent"),
                    () -> assertNotNull(fapiLimit.getStartedAt(), "fapi.get_deposit_limits.limit.startedAt"),
                    () -> assertNotNull(fapiLimit.getExpiresAt(), "fapi.get_deposit_limits.limit.expiresAt"),
                    () -> assertNull(fapiLimit.getDeactivatedAt(), "fapi.get_deposit_limits.limit.deactivatedAt"),
                    () -> assertFalse(fapiLimit.isRequired(), "fapi.get_deposit_limits.limit.isRequired_flag"),
                    () -> {
                        assertNotNull(fapiLimit.getUpcomingChanges(), "fapi.get_deposit_limits.limit.upcomingChanges_list_not_null");
                        assertTrue(fapiLimit.getUpcomingChanges().isEmpty(), "fapi.get_deposit_limits.limit.upcomingChanges_is_empty");
                    }
            );
        });
    }
}
