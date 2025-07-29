package com.uplatform.wallet_tests.tests.wallet.limit.deposit;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.http.fapi.dto.deposit.SetDepositLimitRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitEventType;
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
import java.time.Duration;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверяет сброс потраченной суммы лимита Deposit после истечения периода.
 *
 * Создаётся Deposit лимит со временем начала, почти равным длительности периода
 * (now() - period + 10 секунд). После ожидания 15 секунд выполняется корректировка
 * баланса через CAP. Ожидается событие {@code limit_changed_v2} с типом
 * {@code spent_resetted} и проверяется актуальное состояние лимита во всех компонентах.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> установка лимита Deposit с прошедшим startedAt.</li>
 *   <li><b>Пауза:</b> ожидание истечения периода (15 секунд).</li>
 *   <li><b>Основное действие:</b> корректировка баланса через CAP.</li>
 *   <li><b>Проверка NATS:</b> событие limit_changed_v2 со spent_resetted.</li>
 *   <li><b>Проверка Kafka:</b> сообщение Wallet Projection.</li>
 *   <li><b>Проверка Redis:</b> актуальные данные лимита.</li>
 *   <li><b>Проверка CAP:</b> получение лимитов игрока.</li>
 *   <li><b>Проверка Public API:</b> получение лимитов Deposit.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка лимита Deposit.</li>
 *   <li>NATS: limit_changed_v2.</li>
 *   <li>Kafka: Wallet Projection.</li>
 *   <li>Redis: агрегат кошелька.</li>
 *   <li>CAP API: список лимитов игрока и корректировка баланса.</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("DepositLimit")
@Suite("Позитивные сценарии: DepositLimit")
@Tag("Limits") @Tag("Wallet") @Tag("DepositLimit")
class DepositLimitSpentResetAfterPeriodParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal LIMIT_AMOUNT = new BigDecimal("100.15");
    private static final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("10.00");

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY, 86400),
                arguments(NatsLimitIntervalType.WEEKLY, 604800),
                arguments(NatsLimitIntervalType.MONTHLY, 2592000)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Сброс потраченной суммы Deposit лимита после истечения периода")
    void spentResetAfterPeriod(NatsLimitIntervalType periodType, int periodSeconds) throws Exception {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            SetDepositLimitRequest createRequest;
            CreateBalanceAdjustmentRequest adjustmentRequest;
            NatsMessage<NatsLimitChangedV2Payload> createEvent;
            NatsMessage<NatsLimitChangedV2Payload> resetEvent;
        }
        final TestData ctx = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            int startedAt = (int) (System.currentTimeMillis() / 1000 - periodSeconds + 10);
            ctx.createRequest = SetDepositLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .amount(LIMIT_AMOUNT.toString())
                    .type(periodType)
                    .startedAt(startedAt)
                    .build();

            var response = publicClient.setDepositLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_deposit_limit.status_code");
        });

        step("NATS: получение события limit_changed_v2 о создании", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID()
            );

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            NatsLimitType.DEPOSIT.getValue().equals(payload.getLimits().get(0).getLimitType());

            ctx.createEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();
            assertNotNull(ctx.createEvent, "nats.limit_changed_v2_event.creation");
        });

        step("WAIT: ожидание истечения периода", () -> {
            await().pollDelay(Duration.ofSeconds(15))
                    .atMost(Duration.ofSeconds(16))
                    .until(() -> true);
        });

        step("CAP API: Корректировка баланса после истечения периода", () -> {
            ctx.adjustmentRequest = CreateBalanceAdjustmentRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .amount(ADJUSTMENT_AMOUNT)
                    .reason(ReasonType.MALFUNCTION)
                    .operationType(OperationType.CORRECTION)
                    .direction(DirectionType.INCREASE)
                    .comment("auto-reset")
                    .build();

            var response = capAdminClient.createBalanceAdjustment(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    "6dfe249e-e967-477b-8a42-83efe85c7c3a",
                    ctx.adjustmentRequest
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.create_balance_adjustment.status_code");
        });

        step("NATS: событие limit_changed_v2 о сбросе потраченной суммы", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID()
            );

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            ctx.createEvent.getPayload().getLimits().get(0).getExternalId().equals(payload.getLimits().get(0).getExternalId()) &&
                            NatsLimitEventType.SPENT_RESETTED.getValue().equals(payload.getEventType());

            ctx.resetEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();
            assertNotNull(ctx.resetEvent, "nats.limit_changed_v2_event.reset.message_not_null");

            var limit = ctx.resetEvent.getPayload().getLimits().get(0);
            assertAll("nats.limit_changed_v2_event.reset.content_validation",
                    () -> assertEquals(NatsLimitEventType.SPENT_RESETTED.getValue(), ctx.resetEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.createEvent.getPayload().getLimits().get(0).getExternalId(), limit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.DEPOSIT.getValue(), limit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), limit.getIntervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                    () -> assertEquals(0, LIMIT_AMOUNT.compareTo(limit.getAmount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), limit.getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(limit.getStartedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertNotNull(limit.getExpiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(limit.getStatus(), "nats.limit_changed_v2_event.limit.status")
            );
        });

        step("Kafka Projection: Сравнение данных из NATS и Kafka Wallet Projection", () -> {
            var projectionMsg = walletProjectionKafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.resetEvent.getSequence())
                    .fetch();
            assertNotNull(projectionMsg, "kafka.wallet_projection.message_not_null");
            assertTrue(utils.areEquivalent(projectionMsg, ctx.resetEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis(Wallet): Проверка данных лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.resetEvent.getSequence());

            var redisLimitOpt = aggregate.getLimits().stream()
                    .filter(l -> ctx.resetEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getExternalID()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.deposit_limit_found");
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.resetEvent.getPayload().getLimits().get(0).getExternalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.DEPOSIT.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), redisLimit.getIntervalType(), "redis.wallet_aggregate.limit.intervalType"),
                    () -> assertEquals(0, LIMIT_AMOUNT.compareTo(redisLimit.getAmount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(redisLimit.getSpent()), "redis.wallet_aggregate.limit.spent_is_zero"),
                    () -> assertEquals(0, LIMIT_AMOUNT.compareTo(redisLimit.getRest()), "redis.wallet_aggregate.limit.rest_equals_amount"),
                    () -> assertTrue(redisLimit.isStatus(), "redis.wallet_aggregate.limit.status_is_true")
            );
        });

        step("CAP: Получение лимитов игрока", () -> {
            var response = capAdminClient.getPlayerLimits(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code");
            assertNotNull(response.getBody(), "cap.get_player_limits.response_body_not_null");
            assertNotNull(response.getBody().getData(), "cap.get_player_limits.response_body.data_list_not_null");

            var capLimitOpt = response.getBody().getData().stream()
                    .filter(l -> LIMIT_AMOUNT.compareTo(l.getAmount()) == 0)
                    .findFirst();

            assertTrue(capLimitOpt.isPresent(), "cap.get_player_limits.limit_not_found");
            var capLimit = capLimitOpt.get();

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertTrue(capLimit.isStatus(), "cap.get_player_limits.limit.status_is_true"),
                    () -> assertEquals(periodType.getValue(), capLimit.getPeriod().toString().toLowerCase(), "cap.get_player_limits.limit.intervalType"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), capLimit.getCurrency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, LIMIT_AMOUNT.compareTo(capLimit.getAmount()), "cap.get_player_limits.limit.amount"),
                    () -> assertEquals(0, LIMIT_AMOUNT.compareTo(capLimit.getRest()), "cap.get_player_limits.limit.rest_equals_amount"),
                    () -> {
                        if (capLimit.getSpent() != null) {
                            assertEquals(0, BigDecimal.ZERO.compareTo(capLimit.getSpent()), "cap.get_player_limits.limit.spent_is_zero");
                        }
                    },
                    () -> assertNull(capLimit.getDeactivatedAt(), "cap.get_player_limits.limit.deactivatedAt_is_null"),
                    () -> assertNotNull(capLimit.getStartedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNotNull(capLimit.getExpiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение лимитов игрока", () -> {
            var response = publicClient.getDepositLimits(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_deposit_limits.status_code");
            assertFalse(response.getBody().isEmpty(), "fapi.get_deposit_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> ctx.resetEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getId()))
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_deposit_limits.limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_deposit_limits.limit_content_validation",
                    () -> assertEquals(ctx.resetEvent.getPayload().getLimits().get(0).getExternalId(), fapiLimit.getId(), "fapi.get_deposit_limits.limit.id"),
                    () -> assertEquals(periodType.getValue(), fapiLimit.getType(), "fapi.get_deposit_limits.limit.type"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), fapiLimit.getCurrency(), "fapi.get_deposit_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.isStatus(), "fapi.get_deposit_limits.limit.status_is_true"),
                    () -> assertEquals(0, LIMIT_AMOUNT.compareTo(fapiLimit.getAmount()), "fapi.get_deposit_limits.limit.amount"),
                    () -> assertEquals(0, LIMIT_AMOUNT.compareTo(fapiLimit.getRest()), "fapi.get_deposit_limits.limit.rest_equals_amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(fapiLimit.getSpent()), "fapi.get_deposit_limits.limit.spent_is_zero"),
                    () -> assertNotNull(fapiLimit.getStartedAt(), "fapi.get_deposit_limits.limit.startedAt"),
                    () -> assertNotNull(fapiLimit.getExpiresAt(), "fapi.get_deposit_limits.limit.expiresAt"),
                    () -> assertNull(fapiLimit.getDeactivatedAt(), "fapi.get_deposit_limits.limit.deactivatedAt")
            );
        });
    }
}
