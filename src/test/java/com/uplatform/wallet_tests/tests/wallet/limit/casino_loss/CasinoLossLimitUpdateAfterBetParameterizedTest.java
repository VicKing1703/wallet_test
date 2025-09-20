package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.UpdateTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверяет корректность пересчета лимита CasinoLoss после ставки и уменьшения лимита.
 *
 * Создается лимит CasinoLoss, затем совершается ставка через Manager API. После этого
 * лимит уменьшается PATCH-запросом. Значение {@code amount} принимает новую сумму.
 * Если остаток лимита после ставки меньше новой суммы, поле {@code rest} сохраняет
 * прежний остаток, а {@code spent} равняется разнице между новой суммой и остатком.
 * В противном случае и {@code amount}, и {@code rest} совпадают с новой суммой, а
 * {@code spent} обнуляется.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя и игровой сессии.</li>
 *   <li><b>Основное действие:</b> установка лимита CasinoLoss через Public API.</li>
 *   <li><b>Основное действие:</b> совершение ставки через Manager API.</li>
 *   <li><b>Основное действие:</b> изменение лимита PATCH-запросом в меньшую сторону.</li>
 *   <li><b>Проверка NATS:</b> событие limit_changed_v2 с event_type amount_updated.</li>
 *   <li><b>Проверка Kafka:</b> сообщение Wallet Projection.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька содержит пересчитанный лимит.</li>
 *   <li><b>Проверка CAP:</b> получение лимитов игрока.</li>
 *   <li><b>Проверка Public API:</b> получение лимита CasinoLoss.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: ставка через Manager API.</li>
 *   <li>Public API: установка и изменение лимита CasinoLoss.</li>
 *   <li>NATS: betted_from_gamble, limit_changed_v2.</li>
 *   <li>Kafka: Wallet Projection.</li>
 *   <li>Redis: агрегат кошелька.</li>
 *   <li>CAP API: список лимитов игрока.</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class CasinoLossLimitUpdateAfterBetParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmountBase = new BigDecimal("150.12");
    private static final BigDecimal betAmount = new BigDecimal("10.15");
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
    @DisplayName("Пересчет CasinoLossLimit после ставки и уменьшения лимита")
    void updateCasinoLossLimitAfterBet(NatsLimitIntervalType periodType, BigDecimal newAmount) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            SetCasinoLossLimitRequest createRequest;
            UpdateTurnoverLimitRequest updateRequest;
            BetRequestBody betRequestBody;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            NatsMessage<NatsLimitChangedV2Payload> createEvent;
            NatsMessage<NatsLimitChangedV2Payload> updateEvent;
        }
        final TestData ctx = new TestData();

        BigDecimal oldRest = limitAmountBase.subtract(betAmount);
        BigDecimal expectedSpentAfterUpdate =
                oldRest.compareTo(newAmount) < 0 ? newAmount.subtract(oldRest) : BigDecimal.ZERO;
        BigDecimal expectedRestAfterUpdate =
                oldRest.compareTo(newAmount) < 0 ? oldRest : newAmount;

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            ctx.createRequest = SetCasinoLossLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .type(periodType)
                    .amount(limitAmountBase.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.createRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_casino_loss_limit.status_code");
        });

        step("NATS: получение события limit_changed_v2 о создании лимита", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            NatsLimitType.CASINO_LOSS.getValue().equals(payload.getLimits().get(0).getLimitType());

            ctx.createEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            assertNotNull(ctx.createEvent, "nats.limit_changed_v2_event.creation");
        });

        step("Manager API: Совершение ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertAll("manager_api.bet.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code"),
                    () -> assertEquals(ctx.betRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.bet.body.transactionId")
            );

            step("Sub-step NATS: получение события betted_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.betRequestBody.getTransactionId().equals(payload.getUuid());

                ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

                assertNotNull(ctx.betEvent, "nats.betted_from_gamble_event");
            });
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
                    ctx.registeredPlayer.getWalletData().walletUUID());

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            ctx.createEvent.getPayload().getLimits().get(0).getExternalId().equals(payload.getLimits().get(0).getExternalId()) &&
                            NatsLimitEventType.AMOUNT_UPDATED.getValue().equals(payload.getEventType());

            ctx.updateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            assertNotNull(ctx.updateEvent, "nats.limit_changed_v2_event.update");

            var updLimit = ctx.updateEvent.getPayload().getLimits().get(0);
            assertAll("nats.limit_changed_v2_event.update.content_validation",
                    () -> assertEquals(NatsLimitEventType.AMOUNT_UPDATED.getValue(), ctx.updateEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.createEvent.getPayload().getLimits().get(0).getExternalId(), updLimit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.CASINO_LOSS.getValue(), updLimit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
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
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().walletUUID(),
                    (int) ctx.updateEvent.getSequence());

            assertFalse(aggregate.limits().isEmpty(), "redis.wallet_aggregate.limits_list_not_empty");

            var redisLimitOpt = aggregate.limits().stream()
                    .filter(l -> ctx.updateEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getExternalID()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.casino_loss_limit_found");
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.updateEvent.getPayload().getLimits().get(0).getExternalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.CASINO_LOSS.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
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

        step("Public API: Получение лимитов игрока и их валидация", () -> {
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
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().currency(), fapiLimit.getCurrency(), "fapi.get_casino_loss_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.isStatus(), "fapi.get_casino_loss_limits.limit.status_is_true"),
                    () -> assertEquals(0, newAmount.compareTo(fapiLimit.getAmount()), "fapi.get_casino_loss_limits.limit.amount"),
                    () -> assertEquals(0, expectedRestAfterUpdate.compareTo(fapiLimit.getRest()), "fapi.get_casino_loss_limits.limit.rest"),
                    () -> assertEquals(0, expectedSpentAfterUpdate.compareTo(fapiLimit.getSpent()), "fapi.get_casino_loss_limits.limit.spent"),
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
