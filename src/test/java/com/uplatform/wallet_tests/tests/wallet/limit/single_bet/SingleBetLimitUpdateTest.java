package com.uplatform.wallet_tests.tests.wallet.limit.single_bet;

import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SetSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.UpdateSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет возможность уменьшить лимит на одиночную ставку.
 *
 * Создаётся SingleBet лимит через Public API, затем PATCH-запросом его сумма
 * изменяется на меньшее значение. Тест подтверждает получение события
 * {@code limit_changed_v2} с типом {@code amount_updated} в NATS и проверяет
 * обновление данных в Kafka Wallet Projection, Redis, CAP и Public API.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> установка лимита SingleBet через Public API.</li>
 *   <li><b>Основное действие:</b> изменение лимита PATCH-запросом в меньшую сторону.</li>
 *   <li><b>Проверка NATS:</b> событие limit_changed_v2 с event_type amount_updated.</li>
 *   <li><b>Проверка Kafka:</b> сообщение Wallet Projection.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька содержит обновлённый лимит.</li>
 *   <li><b>Проверка CAP:</b> получение лимитов игрока.</li>
 *   <li><b>Проверка Public API:</b> получение лимита SingleBet.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка и изменение лимита SingleBet.</li>
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
@Feature("SingleBetLimit")
@Suite("Позитивные сценарии: SingleBetLimit")
@Tag("Limits") @Tag("Wallet")
class SingleBetLimitUpdateTest extends BaseTest {

    @Test
    @DisplayName("Изменение single-bet лимита в меньшую сторону")
    void updateSingleBetLimitAmount() {
        final BigDecimal initialAmount = new BigDecimal("100.15");
        final BigDecimal newAmount = new BigDecimal("10");
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            SetSingleBetLimitRequest createRequest;
            UpdateSingleBetLimitRequest updateRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> createEvent;
            NatsMessage<NatsLimitChangedV2Payload> updateEvent;
        }
        final TestData ctx = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на одиночную ставку", () -> {
            ctx.createRequest = SetSingleBetLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .amount(initialAmount.toString())
                    .build();

            var response = publicClient.setSingleBetLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_single_bet_limit.status_code");
        });

        step("Kafka: Получение сообщения из топика limits.v2", () -> {
            var expectedAmount = initialAmount.stripTrailingZeros().toPlainString();
            ctx.kafkaLimitMessage = kafkaClient.expect(LimitMessage.class)
                    .with("playerId", ctx.registeredPlayer.getWalletData().playerUUID())
                    .with("limitType", NatsLimitType.SINGLE_BET.getValue())
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

            ctx.createEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.event_type", NatsLimitEventType.CREATED.getValue())
                    .with("$.limits[0].external_id", ctx.kafkaLimitMessage.id())
                    .with("$.limits[0].limit_type", ctx.kafkaLimitMessage.limitType())
                    .with("$.limits[0].interval_type", "")
                    .with("$.limits[0].amount", ctx.kafkaLimitMessage.amount())
                    .with("$.limits[0].currency_code", ctx.kafkaLimitMessage.currencyCode())
                    .with("$.limits[0].expires_at", 0)
                    .with("$.limits[0].status", true)
                    .fetch();

            assertNotNull(ctx.createEvent, "nats.limit_changed_v2_event.creation.message_not_null");
        });

        step("Public API: Изменение лимита на меньшую сумму", () -> {
            ctx.updateRequest = UpdateSingleBetLimitRequest.builder()
                    .amount(newAmount.toString())
                    .build();

            var response = publicClient.updateSingleLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createEvent.getPayload().getLimits().get(0).getExternalId(),
                    ctx.updateRequest
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.update_single_limit.status_code");
        });

        step("NATS: Проверка события limit_changed_v2 об обновлении суммы", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());

            ctx.updateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.event_type", NatsLimitEventType.AMOUNT_UPDATED.getValue())
                    .with("$.limits[0].external_id", ctx.createEvent.getPayload().getLimits().get(0).getExternalId())
                    .with("$.limits[0].limit_type", NatsLimitType.SINGLE_BET.getValue())
                    .with("$.limits[0].interval_type", "")
                    .with("$.limits[0].amount", newAmount)
                    .with("$.limits[0].currency_code", ctx.registeredPlayer.getWalletData().currency())
                    .with("$.limits[0].expires_at", 0)
                    .with("$.limits[0].status", true)
                    .fetch();

            assertNotNull(ctx.updateEvent, "nats.limit_changed_v2_event.update.message_not_null");

            var updLimit = ctx.updateEvent.getPayload().getLimits().get(0);
            assertAll("nats.limit_changed_v2_event.update.content_validation",
                    () -> assertEquals(NatsLimitEventType.AMOUNT_UPDATED.getValue(), ctx.updateEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.createEvent.getPayload().getLimits().get(0).getExternalId(), updLimit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.SINGLE_BET.getValue(), updLimit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertTrue(updLimit.getIntervalType().isEmpty(), "nats.limit_changed_v2_event.limit.intervalType_empty"),
                    () -> assertEquals(0, newAmount.compareTo(updLimit.getAmount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), updLimit.getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(updLimit.getStartedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertEquals(0, updLimit.getExpiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
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

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.single_bet_limit_found");
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().getLimits().get(0).getExternalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.SINGLE_BET.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertTrue(redisLimit.getIntervalType().isEmpty(), "redis.wallet_aggregate.limit.intervalType_empty"),
                    () -> assertEquals(0, newAmount.compareTo(redisLimit.getAmount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(redisLimit.getSpent()), "redis.wallet_aggregate.limit.spent_is_zero"),
                    () -> assertEquals(0, newAmount.compareTo(redisLimit.getRest()), "redis.wallet_aggregate.limit.rest_equals_amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), redisLimit.getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(redisLimit.getStartedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertEquals(0, redisLimit.getExpiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(redisLimit.isStatus(), "redis.wallet_aggregate.limit.status_is_true")
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
            assertNotNull(response.getBody().getData(), "cap.get_player_limits.response_body.data_list_not_null");

            var capLimitOpt = response.getBody().getData().stream()
                    .filter(l -> newAmount.compareTo(l.getAmount()) == 0)
                    .findFirst();

            assertTrue(capLimitOpt.isPresent(), "cap.get_player_limits.limit_not_found");
            var capLimit = capLimitOpt.get();

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertTrue(capLimit.isStatus(), "cap.get_player_limits.limit.status_is_true"),
                    () -> assertTrue(capLimit.getPeriod().toString().isEmpty(), "cap.get_player_limits.limit.intervalType_empty"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), capLimit.getCurrency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, newAmount.compareTo(capLimit.getAmount()), "cap.get_player_limits.limit.amount"),
                    () -> assertEquals(0, newAmount.compareTo(capLimit.getRest()), "cap.get_player_limits.limit.rest_equals_amount"),
                    () -> assertNotNull(capLimit.getCreatedAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(capLimit.getDeactivatedAt(), "cap.get_player_limits.limit.deactivatedAt_is_null"),
                    () -> assertNotNull(capLimit.getStartedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNull(capLimit.getExpiresAt(), "cap.get_player_limits.limit.expiresAt_is_null")
            );
        });

        step("Public API: Получение лимитов игрока и их валидация после обновления", () -> {
            var response = publicClient.getSingleBetLimits(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_single_bet_limits.status_code");
            assertFalse(response.getBody().isEmpty(), "fapi.get_single_bet_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> ctx.updateEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getId()))
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_single_bet_limits.limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_single_bet_limits.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().getLimits().get(0).getExternalId(), fapiLimit.getId(), "fapi.get_single_bet_limits.limit.id"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), fapiLimit.getCurrency(), "fapi.get_single_bet_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.isStatus(), "fapi.get_single_bet_limits.limit.status_is_true"),
                    () -> assertEquals(0, newAmount.compareTo(fapiLimit.getAmount()), "fapi.get_single_bet_limits.limit.amount"),
                    () -> {
                        assertNotNull(fapiLimit.getUpcomingChanges(), "fapi.get_single_bet_limits.limit.upcomingChanges_list_not_null");
                        assertTrue(fapiLimit.getUpcomingChanges().isEmpty(), "fapi.get_single_bet_limits.limit.upcomingChanges_is_empty");
                    },
                    () -> assertNull(fapiLimit.getDeactivatedAt(), "fapi.get_single_bet_limits.limit.deactivatedAt"),
                    () -> assertTrue(fapiLimit.isRequired(), "fapi.get_single_bet_limits.limit.required")
            );
        });
    }
}
