package com.uplatform.wallet_tests.tests.wallet.limit.turnover;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.UpdateTurnoverLimitRequest;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверяет возможность уменьшить лимит на оборот средств.
 *
 * Создаётся Turnover лимит через Public API, затем PATCH-запросом его сумма
 * изменяется на меньшее значение. Тест подтверждает получение события
 * {@code limit_changed_v2} с типом {@code amount_updated} в NATS и проверяет
 * обновление данных в Kafka Wallet Projection, Redis, CAP и Public API.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> установка лимита Turnover через Public API.</li>
 *   <li><b>Основное действие:</b> изменение лимита PATCH-запросом в меньшую сторону.</li>
 *   <li><b>Проверка NATS:</b> событие limit_changed_v2 с event_type amount_updated.</li>
 *   <li><b>Проверка Kafka:</b> сообщение Wallet Projection.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька содержит обновлённый лимит.</li>
 *   <li><b>Проверка CAP:</b> получение лимитов игрока.</li>
 *   <li><b>Проверка Public API:</b> получение лимита Turnover.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка и изменение лимита Turnover.</li>
 *   <li>NATS: limit_changed_v2.</li>
 *   <li>Kafka: Wallet Projection.</li>
 *   <li>Redis: агрегат кошелька.</li>
 *   <li>CAP API: список лимитов игрока.</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Limits") @Tag("Wallet")
public class TurnoverLimitUpdateParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAmount = new BigDecimal("100.15");
    private static final BigDecimal newAmount = new BigDecimal("10");

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY, true),
                arguments(NatsLimitIntervalType.WEEKLY, true),
                arguments(NatsLimitIntervalType.MONTHLY, true)
        );
    }

    @ParameterizedTest(name = "период = {0}, обязательный = {1}")
    @MethodSource("periodProvider")
    @DisplayName("Изменение turnover лимита в меньшую сторону")
    void updateTurnoverLimitAmount(NatsLimitIntervalType periodType, boolean isLimitRequired) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            SetTurnoverLimitRequest createRequest;
            UpdateTurnoverLimitRequest updateRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> createEvent;
            NatsMessage<NatsLimitChangedV2Payload> updateEvent;
        }
        final TestData ctx = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            ctx.createRequest = SetTurnoverLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .amount(initialAmount.toString())
                    .type(periodType)
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");
        });

        step("Kafka: Получение сообщения из топика limits.v2", () -> {
            var expectedAmount = initialAmount.stripTrailingZeros().toPlainString();
            ctx.kafkaLimitMessage = kafkaClient.expect(LimitMessage.class)
                    .with("playerId", ctx.registeredPlayer.getWalletData().playerUUID())
                    .with("limitType", NatsLimitType.TURNOVER_FUNDS.getValue())
                    .with("currencyCode", ctx.registeredPlayer.getWalletData().currency())
                    .with("amount", expectedAmount
            )
                    .fetch();
            assertNotNull(ctx.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
        });

        step("NATS: Проверка поступления события limit_changed_v2 о создании", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());

            var expectedCreateAmount = initialAmount.stripTrailingZeros().toPlainString();

            ctx.createEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.event_type", NatsLimitEventType.CREATED.getValue())
                    .with("$.limits[0].external_id", ctx.kafkaLimitMessage.id())
                    .with("$.limits[0].limit_type", NatsLimitType.TURNOVER_FUNDS.getValue())
                    .with("$.limits[0].interval_type", periodType.getValue())
                    .with("$.limits[0].currency_code", ctx.registeredPlayer.getWalletData().currency())
                    .with("$.limits[0].amount", expectedCreateAmount)
                    .with("$.limits[0].status", true)
                    .fetch();

            assertNotNull(ctx.createEvent, "nats.limit_changed_v2_event.creation.message_not_null");
        });

        step("Public API: Изменение лимита на меньшую сумму", () -> {
            ctx.updateRequest = UpdateTurnoverLimitRequest.builder()
                    .amount(newAmount.toString())
                    .build();

            var response = publicClient.updateRecalculatedLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createEvent.getPayload().limits().get(0).externalId(),
                    ctx.updateRequest
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.update_recalculated_limit.status_code");
        });

        step("NATS: Проверка события limit_changed_v2 об обновлении суммы", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());

            var expectedUpdateAmount = newAmount.stripTrailingZeros().toPlainString();

            ctx.updateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.event_type", NatsLimitEventType.AMOUNT_UPDATED.getValue())
                    .with("$.limits[0].external_id", ctx.createEvent.getPayload().limits().get(0).externalId())
                    .with("$.limits[0].limit_type", NatsLimitType.TURNOVER_FUNDS.getValue())
                    .with("$.limits[0].interval_type", periodType.getValue())
                    .with("$.limits[0].currency_code", ctx.registeredPlayer.getWalletData().currency())
                    .with("$.limits[0].amount", expectedUpdateAmount)
                    .fetch();

            assertNotNull(ctx.updateEvent, "nats.limit_changed_v2_event.update.message_not_null");

            var updLimit = ctx.updateEvent.getPayload().limits().get(0);
            assertAll("nats.limit_changed_v2_event.update.content_validation",
                    () -> assertEquals(NatsLimitEventType.AMOUNT_UPDATED.getValue(), ctx.updateEvent.getPayload().eventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.createEvent.getPayload().limits().get(0).externalId(), updLimit.externalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), updLimit.limitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), updLimit.intervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                    () -> assertEquals(0, newAmount.compareTo(updLimit.amount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), updLimit.currencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(updLimit.startedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertNotNull(updLimit.expiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(updLimit.status(), "nats.limit_changed_v2_event.limit.status")
            );
        });

        step("Kafka Projection: Сравнение данных из NATS и Kafka Wallet Projection", () -> {
            var projectionMsg = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.updateEvent.sequence())
                    .fetch();
            assertNotNull(projectionMsg, "kafka.wallet_projection.message_not_null");
            assertTrue(utils.areEquivalent(projectionMsg, ctx.updateEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis(Wallet): Проверка данных лимита в агрегате", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.updateEvent.sequence())
                    .fetch();

            assertFalse(aggregate.limits().isEmpty(), "redis.wallet_aggregate.limits_list_not_empty");

            var redisLimitOpt = aggregate.limits().stream()
                    .filter(l -> ctx.updateEvent.getPayload().limits().get(0).externalId().equals(l.getExternalID()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.turnover_limit_found");
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().limits().get(0).externalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), redisLimit.getIntervalType(), "redis.wallet_aggregate.limit.intervalType"),
                    () -> assertEquals(0, newAmount.compareTo(redisLimit.amount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(redisLimit.spent()), "redis.wallet_aggregate.limit.spent_is_zero"),
                    () -> assertEquals(0, newAmount.compareTo(redisLimit.rest()), "redis.wallet_aggregate.limit.rest_equals_amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), redisLimit.getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(redisLimit.startedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertNotNull(redisLimit.expiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(redisLimit.status(), "redis.wallet_aggregate.limit.status_is_true")
            );
        });

        step("CAP: Получение лимитов игрока и их валидация после обновления", () -> {
            var response = capAdminClient.getPlayerLimits(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code");
            assertNotNull(response.getBody(), "cap.get_player_limits.response_body_not_null");
            assertNotNull(response.getBody().data(), "cap.get_player_limits.response_body.data_list_not_null");

            var capLimitOpt = response.getBody().data().stream()
                    .filter(l -> newAmount.compareTo(l.amount()) == 0)
                    .findFirst();

            assertTrue(capLimitOpt.isPresent(), "cap.get_player_limits.limit_not_found");
            var capLimit = capLimitOpt.get();

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertTrue(capLimit.status(), "cap.get_player_limits.limit.status_is_true"),
                    () -> assertEquals(periodType.getValue(), capLimit.period().toString().toLowerCase(), "cap.get_player_limits.limit.intervalType"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), capLimit.currency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, newAmount.compareTo(capLimit.amount()), "cap.get_player_limits.limit.amount"),
                    () -> assertEquals(0, newAmount.compareTo(capLimit.rest()), "cap.get_player_limits.limit.rest_equals_amount"),
                    () -> assertNotNull(capLimit.createdAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(capLimit.deactivatedAt(), "cap.get_player_limits.limit.deactivatedAt_is_null"),
                    () -> assertNotNull(capLimit.startedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNotNull(capLimit.expiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение лимитов игрока и их валидация после обновления", () -> {
            var response = publicClient.getTurnoverLimits(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_turnover_limits.status_code");
            assertFalse(response.getBody().isEmpty(), "fapi.get_turnover_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> ctx.updateEvent.getPayload().limits().get(0).externalId().equals(l.id()))
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_turnover_limits.limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_turnover_limits.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().limits().get(0).externalId(), fapiLimit.id(), "fapi.get_turnover_limits.limit.id"),
                    () -> assertEquals(periodType.getValue(), fapiLimit.type(), "fapi.get_turnover_limits.limit.type"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), fapiLimit.currency(), "fapi.get_turnover_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.status(), "fapi.get_turnover_limits.limit.status_is_true"),
                    () -> assertEquals(0, newAmount.compareTo(fapiLimit.amount()), "fapi.get_turnover_limits.limit.amount"),
                    () -> assertEquals(0, newAmount.compareTo(fapiLimit.rest()), "fapi.get_turnover_limits.limit.rest_equals_amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(fapiLimit.spent()), "fapi.get_turnover_limits.limit.spent_is_zero"),
                    () -> assertNotNull(fapiLimit.startedAt(), "fapi.get_turnover_limits.limit.startedAt"),
                    () -> assertNotNull(fapiLimit.expiresAt(), "fapi.get_turnover_limits.limit.expiresAt"),
                    () -> assertNull(fapiLimit.deactivatedAt(), "fapi.get_turnover_limits.limit.deactivatedAt"),
                    () -> assertEquals(isLimitRequired, fapiLimit.required(), "fapi.get_turnover_limits.limit.isRequired_flag"),
                    () -> {
                        assertNotNull(fapiLimit.upcomingChanges(), "fapi.get_turnover_limits.limit.upcomingChanges_list_not_null");
                        assertTrue(fapiLimit.upcomingChanges().isEmpty(), "fapi.get_turnover_limits.limit.upcomingChanges_is_empty");
                    }
            );
        });
    }
}
