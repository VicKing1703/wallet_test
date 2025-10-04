package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.testing.multisource.api.nats.dto.NatsMessage;
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
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий процесс создания лимита на оборот средств (TurnoverLimit)
 * с различными периодами действия (ежедневный, еженедельный, ежемесячный)
 * и его корректное отражение в различных системах.
 *
 * <p>Каждая итерация параметризованного теста выполняется с полностью изолированным состоянием,
 * включая создание нового игрока.</p>
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API:
 *     <ul>
 *       <li>Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *       <li>Получение списка лимитов игрока через FAPI ({@code /profile/limit/turnover}).</li>
 *     </ul>
 *   </li>
 *   <li>Control Admin Panel (CAP) API: Получение лимитов игрока.</li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limits.v2} через Kafka при установке лимита.</li>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Проверка консистентности данных между Kafka (проекция кошелька) и NATS.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Проверка данных созданного лимита в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>}).</li>
 * </ul>
 *
 * <p><b>Проверяемые типы периодов лимита ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType}):</b></p>
 * <ul>
 *   <li>{@code DAILY} - ежедневный.</li>
 *   <li>{@code WEEKLY} - еженедельный.</li>
 *   <li>{@code MONTHLY} - ежемесячный.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Limits") @Tag("Wallet") @Tag("TurnoverLimit")
public class TurnoverLimitCreateParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY, true),
                arguments(NatsLimitIntervalType.WEEKLY, true),
                arguments(NatsLimitIntervalType.MONTHLY, true)
        );
    }

    /**
     * @param periodType      Тип периода для устанавливаемого лимита.
     * @param isLimitRequired Флаг, указывающий, является ли лимит обязательным (проверяется в FAPI).
     */
    @ParameterizedTest(name = "период = {0}, обязательный = {1}")
    @MethodSource("periodProvider")
    @DisplayName("Создание, проверка и получение TurnoverLimit:")
    void testCreateAndVerifyTurnoverLimit(NatsLimitIntervalType periodType, boolean isLimitRequired) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            SetTurnoverLimitRequest setLimitRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> natsLimitChangeEvent;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API (FAPI): Установка лимита на оборот средств", () -> {
            ctx.setLimitRequest = SetTurnoverLimitRequest.builder()
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .type(periodType)
                    .amount(limitAmountBase.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    ctx.registeredPlayer.authorizationResponse().getBody().getToken(),
                    ctx.setLimitRequest);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");
        });

        step("Kafka: Проверка получения события limits.v2", () -> {
            var expectedAmount = limitAmountBase.stripTrailingZeros().toPlainString();
            ctx.kafkaLimitMessage = kafkaClient.expect(LimitMessage.class)
                    .with("playerId", ctx.registeredPlayer.walletData().playerUUID())
                    .with("limitType", NatsLimitType.TURNOVER_FUNDS.getValue())
                    .with("currencyCode", ctx.registeredPlayer.walletData().currency())
                    .with("amount", expectedAmount
            )
                    .fetch();

            assertNotNull(ctx.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
            assertAll("kafka.limits_v2_event.content_validation",
                    () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), ctx.kafkaLimitMessage.limitType(), "kafka.limits_v2_event.limitType"),
                    () -> assertEquals(periodType.getValue(), ctx.kafkaLimitMessage.intervalType(), "kafka.limits_v2_event.intervalType"),
                    () -> assertEquals(0, limitAmountBase.compareTo(new BigDecimal(ctx.kafkaLimitMessage.amount())), "kafka.limits_v2_event.amount"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), ctx.kafkaLimitMessage.currencyCode(), "kafka.limits_v2_event.currencyCode"),
                    () -> assertNotNull(ctx.kafkaLimitMessage.id(), "kafka.limits_v2_event.id"),
                    () -> assertNotNull(ctx.kafkaLimitMessage.startedAt(), "kafka.limits_v2_event.startedAt"),
                    () -> assertNotNull(ctx.kafkaLimitMessage.expiresAt(), "kafka.limits_v2_event.expiresAt")
            );
        });

        step("NATS: Проверка получения события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            var expectedAmount = limitAmountBase.stripTrailingZeros().toPlainString();

            ctx.natsLimitChangeEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.event_type", NatsLimitEventType.CREATED.getValue())
                    .with("$.limits[0].external_id", ctx.kafkaLimitMessage.id())
                    .with("$.limits[0].limit_type", NatsLimitType.TURNOVER_FUNDS.getValue())
                    .with("$.limits[0].interval_type", periodType.getValue())
                    .with("$.limits[0].currency_code", ctx.registeredPlayer.walletData().currency())
                    .with("$.limits[0].amount", expectedAmount)
                    .with("$.limits[0].expires_at", ctx.kafkaLimitMessage.expiresAt())
                    .with("$.limits[0].status", true)
                    .fetch();
            assertNotNull(ctx.natsLimitChangeEvent, "nats.limit_changed_v2_event.message_not_null");
            assertNotNull(ctx.natsLimitChangeEvent.getPayload(), "nats.limit_changed_v2_event.payload_not_null");
            assertNotNull(ctx.natsLimitChangeEvent.getPayload().limits(), "nats.limit_changed_v2_event.payload.limits_list_not_null");
            assertFalse(ctx.natsLimitChangeEvent.getPayload().limits().isEmpty(), "nats.limit_changed_v2_event.payload.limits_list_not_empty");

            var natsLimit = ctx.natsLimitChangeEvent.getPayload().limits().get(0);
            assertAll("nats.limit_changed_v2_event.content_validation",
                    () -> assertEquals(NatsLimitEventType.CREATED.getValue(), ctx.natsLimitChangeEvent.getPayload().eventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.kafkaLimitMessage.id(), natsLimit.externalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), natsLimit.limitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), natsLimit.intervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                    () -> assertEquals(0, limitAmountBase.compareTo(natsLimit.amount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), natsLimit.currencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(natsLimit.startedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertEquals(ctx.kafkaLimitMessage.expiresAt(), natsLimit.expiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(natsLimit.status(), "nats.limit_changed_v2_event.limit.status_is_true")
            );
        });

        step("Kafka Projection: Сравнение данных из NATS и Kafka Wallet Projection", () -> {
            var projectionMsg = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.natsLimitChangeEvent.getSequence())
                    .fetch();
            assertNotNull(projectionMsg, "kafka.wallet_projection.message_not_null");
            assertTrue(utils.areEquivalent(projectionMsg, ctx.natsLimitChangeEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis (Wallet Aggregate): Проверка данных лимита в агрегате кошелька", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.natsLimitChangeEvent.getSequence())
                    .fetch();

            assertFalse(aggregate.limits().isEmpty(), "redis.wallet_aggregate.limits_list_not_empty");

            var redisLimitOpt = aggregate.limits().stream()
                    .filter(l ->
                            ctx.natsLimitChangeEvent.getPayload().limits().get(0).externalId().equals(l.getExternalID()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.turnover_limit_found_period_" + periodType.getValue());
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.natsLimitChangeEvent.getPayload().limits().get(0).externalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), redisLimit.getIntervalType(), "redis.wallet_aggregate.limit.intervalType"),
                    () -> assertEquals(0, limitAmountBase.compareTo(redisLimit.amount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(redisLimit.spent()), "redis.wallet_aggregate.limit.spent_is_zero"),
                    () -> assertEquals(0, limitAmountBase.compareTo(redisLimit.rest()), "redis.wallet_aggregate.limit.rest_equals_amount"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), redisLimit.getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(redisLimit.startedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertNotNull(redisLimit.expiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(redisLimit.status(), "redis.wallet_aggregate.limit.status_is_true")
            );
        });

        step("CAP: Получение и валидация лимитов игрока", () -> {
            var response = capAdminClient.getPlayerLimits(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code");
            assertNotNull(response.getBody(), "cap.get_player_limits.response_body_not_null");
            assertNotNull(response.getBody().data(), "cap.get_player_limits.response_body.data_list_not_null");

            var capLimitOpt = response.getBody().data().stream()
                    .filter(l -> limitAmountBase.compareTo(l.amount()) == 0)
                    .findFirst();

            assertTrue(capLimitOpt.isPresent(), "cap.get_player_limits.limit_not_found");
            var capLimit = capLimitOpt.get();

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertTrue(capLimit.status(), "cap.get_player_limits.limit.status_is_true"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), capLimit.currency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, limitAmountBase.compareTo(capLimit.amount()), "cap.get_player_limits.limit.amount"),
                    () -> {
                        assertNotNull(capLimit.rest(), "cap.get_player_limits.limit.rest_is_not_null");
                        assertEquals(0, limitAmountBase.compareTo(capLimit.rest()), "cap.get_player_limits.limit.rest_equals_amount");
                    },
                    () -> assertNull(capLimit.spent(), "cap.get_player_limits.limit.spent_is_not_null"),
                    () -> assertNotNull(capLimit.createdAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(capLimit.deactivatedAt(), "cap.get_player_limits.limit.deactivatedAt_is_null_for_active"),
                    () -> assertNotNull(capLimit.startedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNotNull(capLimit.expiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение и валидация списка лимитов игрока", () -> {
            var response = publicClient.getTurnoverLimits(
                    ctx.registeredPlayer.authorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_turnover_limits.status_code");
            assertNotNull(response.getBody(), "fapi.get_turnover_limits.response_body_not_null");
            assertFalse(response.getBody().isEmpty(), "fapi.get_turnover_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> {
                        boolean idMatch = ctx.natsLimitChangeEvent.getPayload().limits().get(0).externalId().equals(l.id());
                        boolean typeMatch = periodType.getValue().equalsIgnoreCase(l.type());
                        return idMatch && typeMatch;
                    })
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_turnover_limits.turnover_limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_turnover_limits.limit_content_validation",
                    () -> assertEquals(ctx.natsLimitChangeEvent.getPayload().limits().get(0).externalId(), fapiLimit.id(), "fapi.get_turnover_limits.limit.id"),
                    () -> assertEquals(periodType.getValue(), fapiLimit.type(), "fapi.get_turnover_limits.limit.type_period"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), fapiLimit.currency(), "fapi.get_turnover_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.status(), "fapi.get_turnover_limits.limit.status_is_true"),
                    () -> assertEquals(0, limitAmountBase.compareTo(fapiLimit.amount()), "fapi.get_turnover_limits.limit.amount"),
                    () -> assertEquals(0, limitAmountBase.compareTo(fapiLimit.rest()), "fapi.get_turnover_limits.limit.rest_equals_amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(fapiLimit.spent()), "fapi.get_turnover_limits.limit.spent_is_zero"),
                    () -> assertNotNull(fapiLimit.startedAt(), "fapi.get_turnover_limits.limit.startedAt"),
                    () -> assertNotNull(fapiLimit.expiresAt(), "fapi.get_turnover_limits.limit.expiresAt"),
                    () -> assertNull(fapiLimit.deactivatedAt(), "fapi.get_turnover_limits.limit.deactivatedAt_is_null_for_active_limit"),
                    () -> assertEquals(isLimitRequired, fapiLimit.required(), "fapi.get_turnover_limits.limit.isRequired_flag"),
                    () -> {
                        assertNotNull(fapiLimit.upcomingChanges(), "fapi.get_turnover_limits.limit.upcomingChanges_list_not_null");
                        assertTrue(fapiLimit.upcomingChanges().isEmpty(), "fapi.get_turnover_limits.limit.upcomingChanges_is_empty_for_new");
                    }
            );
        });
    }
}
