package com.uplatform.wallet_tests.tests.wallet.limit.single_bet;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SetSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.testing.multisource.api.nats.dto.NatsMessage;
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

@Severity(SeverityLevel.BLOCKER)
@Epic("Limits")
@Feature("SingleBetLimit")
@Suite("Позитивные сценарии: SingleBetLimit")
@Tag("Limits") @Tag("Wallet")
public class SingleBetLimitCreateTest extends BaseTest {

    @Test
    @DisplayName("Создание single-bet лимита")
    void createSingleBetLimit() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            BigDecimal limitAmount;
            SetSingleBetLimitRequest singleBetLimitRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount = new BigDecimal("100.15");

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на одиночную ставку", () -> {
            ctx.singleBetLimitRequest = SetSingleBetLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .amount(ctx.limitAmount.toString())
                    .build();

            var response = publicClient.setSingleBetLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.singleBetLimitRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_single_bet_limit.status_code");
        });

        step("Kafka: Получение сообщения из топика limits.v2", () -> {
            var expectedAmount = ctx.limitAmount.stripTrailingZeros().toPlainString();
            ctx.kafkaLimitMessage = kafkaClient.expect(LimitMessage.class)
                    .with("playerId", ctx.registeredPlayer.getWalletData().playerUUID())
                    .with("limitType", NatsLimitType.SINGLE_BET.getValue())
                    .with("currencyCode", ctx.registeredPlayer.getWalletData().currency())
                    .with("amount", expectedAmount
            )
                    .fetch();
            assertNotNull(ctx.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
        });

        step("NATS: Проверка поступления события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());

            ctx.limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
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

            assertAll("nats.limit_changed_v2_event.content_validation",
                    () -> assertEquals(NatsLimitEventType.CREATED.getValue(), ctx.limitCreateEvent.getPayload().eventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.kafkaLimitMessage.id(), ctx.limitCreateEvent.getPayload().limits().get(0).externalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(ctx.kafkaLimitMessage.limitType(), ctx.limitCreateEvent.getPayload().limits().get(0).limitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertTrue(ctx.limitCreateEvent.getPayload().limits().get(0).intervalType().isEmpty(), "nats.limit_changed_v2_event.limit.intervalType_empty"),
                    () -> assertEquals(ctx.kafkaLimitMessage.amount(), ctx.limitCreateEvent.getPayload().limits().get(0).amount().toString(), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(ctx.kafkaLimitMessage.currencyCode(), ctx.limitCreateEvent.getPayload().limits().get(0).currencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(ctx.limitCreateEvent.getPayload().limits().get(0).startedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertEquals(0, ctx.limitCreateEvent.getPayload().limits().get(0).expiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(ctx.limitCreateEvent.getPayload().limits().get(0).status(), "nats.limit_changed_v2_event.limit.status")
            );
        });

        step("Kafka: Сравнение сообщения из Kafka с событием из NATS", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.limitCreateEvent.getSequence())
                    .fetch();
            assertTrue(utils.areEquivalent(kafkaMessage, ctx.limitCreateEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.limitCreateEvent.getSequence())
                    .fetch();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).externalId(), aggregate.limits().get(0).getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).limitType(), aggregate.limits().get(0).limitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertTrue(ctx.limitCreateEvent.getPayload().limits().get(0).intervalType().isEmpty(), "redis.wallet_aggregate.limit.intervalType_empty"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).amount(), aggregate.limits().get(0).amount(), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(BigDecimal.ZERO, aggregate.limits().get(0).spent(), "redis.wallet_aggregate.limit.spent_zero"),
                    () -> assertEquals(0, ctx.limitCreateEvent.getPayload().limits().get(0).amount().compareTo(aggregate.limits().get(0).rest()), "redis.wallet_aggregate.limit.rest"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).currencyCode(), aggregate.limits().get(0).currencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(ctx.limitCreateEvent.getPayload().limits().get(0).startedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertEquals(0, aggregate.limits().get(0).expiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(aggregate.limits().get(0).status(), "redis.wallet_aggregate.limit.status")
            );
        });

        step("CAP: Получение лимитов игрока и их валидация", () -> {
            var response = capAdminClient.getPlayerLimits(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code"),
                    () -> assertTrue(response.getBody().data().get(0).status(), "cap.get_player_limits.limit.status"),
                    () -> assertTrue(response.getBody().data().get(0).period().toString().isEmpty(), "cap.get_player_limits.limit.intervalType_empty"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).currencyCode(), response.getBody().data().get(0).currency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).amount(), response.getBody().data().get(0).amount(), "cap.get_player_limits.limit.amount"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).amount(), response.getBody().data().get(0).rest(), "cap.get_player_limits.limit.rest"),
                    () -> assertNotNull(response.getBody().data().get(0).createdAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(response.getBody().data().get(0).deactivatedAt(), "cap.get_player_limits.limit.deactivatedAt"),
                    () -> assertNotNull(response.getBody().data().get(0).startedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNull(response.getBody().data().get(0).expiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение лимитов игрока и их валидация", () -> {
            var response = publicClient.getSingleBetLimits(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertAll("fapi.get_single_bet_limits.limit_content_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_single_bet_limits.status_code"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).externalId(), response.getBody().get(0).id(), "fapi.get_single_bet_limits.limit.id"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).currencyCode(), response.getBody().get(0).currency(), "fapi.get_single_bet_limits.limit.currency"),
                    () -> assertTrue(response.getBody().get(0).status(), "fapi.get_single_bet_limits.limit.status"),
                    () -> assertEquals(ctx.limitCreateEvent.getPayload().limits().get(0).amount(), response.getBody().get(0).amount(), "fapi.get_single_bet_limits.limit.amount"),
                    () -> assertTrue(response.getBody().get(0).upcomingChanges().isEmpty(), "fapi.get_single_bet_limits.limit.upcomingChanges_empty"),
                    () -> assertNull(response.getBody().get(0).deactivatedAt(), "fapi.get_single_bet_limits.limit.deactivatedAt"),
                    () -> assertTrue(response.getBody().get(0).required(), "fapi.get_single_bet_limits.limit.required")
            );
        });
    }
}
