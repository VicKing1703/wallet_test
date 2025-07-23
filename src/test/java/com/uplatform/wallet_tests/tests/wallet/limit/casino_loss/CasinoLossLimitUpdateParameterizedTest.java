package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.UpdateTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
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
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверяет возможность уменьшить лимит на проигрыш в казино.
 *
 * Создаётся CasinoLoss лимит через Public API, затем PATCH-запросом его сумма
 * изменяется на меньшее значение. Тест подтверждает получение события
 * {@code limit_changed_v2} с типом {@code amount_updated} в NATS и проверяет
 * обновление данных в Kafka Wallet Projection, Redis, CAP и Public API.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> установка лимита CasinoLoss через Public API.</li>
 *   <li><b>Основное действие:</b> изменение лимита PATCH-запросом в меньшую сторону.</li>
 *   <li><b>Проверка NATS:</b> событие limit_changed_v2 с event_type amount_updated.</li>
 *   <li><b>Проверка Kafka:</b> сообщение Wallet Projection.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька содержит обновлённый лимит.</li>
 *   <li><b>Проверка CAP:</b> получение лимитов игрока.</li>
 *   <li><b>Проверка Public API:</b> получение лимита CasinoLoss.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка и изменение лимита CasinoLoss.</li>
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
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Limits") @Tag("Wallet") @Tag("CasinoLossLimit")
public class CasinoLossLimitUpdateParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAmount = new BigDecimal("100.15");
    private static final BigDecimal newAmount = new BigDecimal("10");

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Изменение casino-loss лимита в меньшую сторону")
    void updateCasinoLossLimitAmount(NatsLimitIntervalType periodType) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            SetCasinoLossLimitRequest createRequest;
            UpdateTurnoverLimitRequest updateRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> createEvent;
            NatsMessage<NatsLimitChangedV2Payload> updateEvent;
        }
        final TestData ctx = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            ctx.createRequest = SetCasinoLossLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .amount(initialAmount.toString())
                    .type(periodType)
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_casino_loss_limit.status_code");
        });

        step("Kafka: Получение сообщения из топика limits.v2", () -> {
            var expectedAmount = initialAmount.stripTrailingZeros().toPlainString();
            ctx.kafkaLimitMessage = limitKafkaClient.expectLimitMessage(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    NatsLimitType.CASINO_LOSS.getValue(),
                    ctx.registeredPlayer.getWalletData().getCurrency(),
                    expectedAmount
            );
            assertNotNull(ctx.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
        });

        step("NATS: Проверка поступления события limit_changed_v2 о создании", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID()
            );

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            ctx.kafkaLimitMessage.getId().equals(payload.getLimits().get(0).getExternalId());

            ctx.createEvent = natsClient.findMessageAsync(
                    subject,
                    NatsLimitChangedV2Payload.class,
                    filter
            ).get();

            assertNotNull(ctx.createEvent, "nats.limit_changed_v2_event.creation.message_not_null");
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

        step("NATS: Проверка события limit_changed_v2 об обновлении суммы", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID()
            );

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            ctx.createEvent.getPayload().getLimits().get(0).getExternalId().equals(payload.getLimits().get(0).getExternalId()) &&
                            NatsLimitEventType.AMOUNT_UPDATED.getValue().equals(payload.getEventType());

            ctx.updateEvent = natsClient.findMessageAsync(
                    subject,
                    NatsLimitChangedV2Payload.class,
                    filter
            ).get();

            assertNotNull(ctx.updateEvent, "nats.limit_changed_v2_event.update.message_not_null");

            var updLimit = ctx.updateEvent.getPayload().getLimits().get(0);
            assertAll("nats.limit_changed_v2_event.update.content_validation",
                    () -> assertEquals(NatsLimitEventType.AMOUNT_UPDATED.getValue(), ctx.updateEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.createEvent.getPayload().getLimits().get(0).getExternalId(), updLimit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.CASINO_LOSS.getValue(), updLimit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), updLimit.getIntervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                    () -> assertEquals(0, newAmount.compareTo(updLimit.getAmount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), updLimit.getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(updLimit.getStartedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertNotNull(updLimit.getExpiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(updLimit.getStatus(), "nats.limit_changed_v2_event.limit.status")
            );
        });

        step("Kafka Projection: Сравнение данных из NATS и Kafka Wallet Projection", () -> {
            var projectionMsg = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.updateEvent.getSequence());
            assertNotNull(projectionMsg, "kafka.wallet_projection.message_not_null");
            assertTrue(utils.areEquivalent(projectionMsg, ctx.updateEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis(Wallet): Проверка данных лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.updateEvent.getSequence());

            assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet_aggregate.limits_list_not_empty");

            var redisLimitOpt = aggregate.getLimits().stream()
                    .filter(l -> ctx.updateEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getExternalID()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.casino_loss_limit_found");
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().getLimits().get(0).getExternalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.CASINO_LOSS.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), redisLimit.getIntervalType(), "redis.wallet_aggregate.limit.intervalType"),
                    () -> assertEquals(0, newAmount.compareTo(redisLimit.getAmount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(redisLimit.getSpent()), "redis.wallet_aggregate.limit.spent_is_zero"),
                    () -> assertEquals(0, newAmount.compareTo(redisLimit.getRest()), "redis.wallet_aggregate.limit.rest_equals_amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), redisLimit.getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(redisLimit.getStartedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertNotNull(redisLimit.getExpiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(redisLimit.isStatus(), "redis.wallet_aggregate.limit.status_is_true")
            );
        });

        step("CAP: Получение лимитов игрока и их валидация после обновления", () -> {
            var response = capAdminClient.getPlayerLimits(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
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
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), capLimit.getCurrency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, newAmount.compareTo(capLimit.getAmount()), "cap.get_player_limits.limit.amount"),
                    () -> assertEquals(0, newAmount.compareTo(capLimit.getRest()), "cap.get_player_limits.limit.rest_equals_amount"),
                    () -> assertNotNull(capLimit.getCreatedAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(capLimit.getDeactivatedAt(), "cap.get_player_limits.limit.deactivatedAt_is_null"),
                    () -> assertNotNull(capLimit.getStartedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNotNull(capLimit.getExpiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение лимитов игрока и их валидация после обновления", () -> {
            var response = publicClient.getCasinoLossLimits(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_casino_loss_limits.status_code");
            assertFalse(response.getBody().isEmpty(), "fapi.get_casino_loss_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> ctx.updateEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getId()))
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_casino_loss_limits.limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_casino_loss_limits.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().getLimits().get(0).getExternalId(), fapiLimit.getId(), "fapi.get_casino_loss_limits.limit.id"),
                    () -> assertEquals(periodType.getValue(), fapiLimit.getType(), "fapi.get_casino_loss_limits.limit.type"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), fapiLimit.getCurrency(), "fapi.get_casino_loss_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.isStatus(), "fapi.get_casino_loss_limits.limit.status_is_true"),
                    () -> assertEquals(0, newAmount.compareTo(fapiLimit.getAmount()), "fapi.get_casino_loss_limits.limit.amount"),
                    () -> assertEquals(0, newAmount.compareTo(fapiLimit.getRest()), "fapi.get_casino_loss_limits.limit.rest_equals_amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(fapiLimit.getSpent()), "fapi.get_casino_loss_limits.limit.spent_is_zero"),
                    () -> assertNotNull(fapiLimit.getStartedAt(), "fapi.get_casino_loss_limits.limit.startedAt"),
                    () -> assertNotNull(fapiLimit.getExpiresAt(), "fapi.get_casino_loss_limits.limit.expiresAt"),
                    () -> assertNull(fapiLimit.getDeactivatedAt(), "fapi.get_casino_loss_limits.limit.deactivatedAt"),
                    () -> assertFalse(fapiLimit.isRequired(), "fapi.get_casino_loss_limits.limit.isRequired_flag"),
                    () -> {
                        assertNotNull(fapiLimit.getUpcomingChanges(), "fapi.get_casino_loss_limits.limit.upcomingChanges_list_not_null");
                        assertTrue(fapiLimit.getUpcomingChanges().isEmpty(), "fapi.get_casino_loss_limits.limit.upcomingChanges_is_empty");
                    }
            );
        });
    }
}
