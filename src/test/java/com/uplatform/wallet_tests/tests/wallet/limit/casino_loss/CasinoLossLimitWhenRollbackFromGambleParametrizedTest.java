package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверка уменьшения остатка лимита CasinoLoss при роллбэке в казино.
 *
 * <p>Тест создает лимит на проигрыш для разных периодов, совершает ставку,
 * затем выполняет роллбэк и убеждается в корректном изменении значений
 * {@code rest} и {@code spent} лимита.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового игрока.</li>
 *   <li><b>Создание игровой сессии:</b> через DefaultTestSteps.</li>
 *   <li><b>Установка лимита:</b> Public API {@code /profile/limit/casino-loss}.</li>
 *   <li><b>Проверка NATS:</b> событие {@code limit_changed_v2}.</li>
 *   <li><b>Основное действие:</b> ставка и роллбэк через Manager API.</li>
 *   <li><b>Проверка NATS:</b> событие {@code rollbacked_from_gamble}.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька обновлен.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка лимита.</li>
 *   <li>Manager API: ставка и роллбэк.</li>
 *   <li>NATS: события limit_changed_v2 и rollbacked_from_gamble.</li>
 *   <li>Redis: агрегат кошелька.</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class CasinoLossLimitWhenRollbackFromGambleParametrizedTest extends BaseParameterizedTest {

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Изменение остатка CasinoLossLimit при получении роллбэка в казино")
    void test(NatsLimitIntervalType periodType) {
        final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RollbackRequestBody rollbackRequestBody;
            NatsMessage<NatsGamblingEventPayload> rollbackEvent;
            BigDecimal limitAmount;
            BigDecimal betAmount;
            BigDecimal rollbackAmount;
            BigDecimal expectedRestAmountAfterRollback;
            BigDecimal expectedSpentAmountAfterRollback;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount =  new BigDecimal("150.12");
        ctx.betAmount = new BigDecimal("10.15");
        ctx.rollbackAmount = ctx.betAmount;
        ctx.expectedSpentAmountAfterRollback = BigDecimal.ZERO;
        ctx.expectedRestAmountAfterRollback = ctx.limitAmount;

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(new BigDecimal("2000.00"));
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            var request = SetCasinoLossLimitRequest.builder()
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .type(periodType)
                    .amount(ctx.limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    ctx.registeredPlayer.authorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_casino_loss_limit.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.walletData().playerUUID(),
                        ctx.registeredPlayer.walletData().walletUUID());

                var limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .fetch();

                assertNotNull(limitCreateEvent, "nats.limit_changed_v2_event");
        });
        });

        step("Manager API: Совершение ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(ctx.betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("Manager API: Получение роллбэка", () -> {
            ctx.rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .amount(ctx.rollbackAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(ctx.betRequestBody.getTransactionId())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .roundClosed(true)
                    .build();

            var response = managerClient.rollback(
                    casinoId,
                    utils.createSignature(ApiEndpoints.ROLLBACK, ctx.rollbackRequestBody),
                    ctx.rollbackRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code");

            step("Sub-step NATS: Проверка поступления события rollbacked_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.walletData().playerUUID(),
                        ctx.registeredPlayer.walletData().walletUUID());

                ctx.rollbackEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.ROLLBACKED_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.rollbackRequestBody.getTransactionId())
                    .fetch();

                assertNotNull(ctx.rollbackEvent, "nats.rollbacked_from_gamble_event");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.rollbackEvent.getSequence())
                    .fetch();

            assertAll(
                    () -> assertEquals(0, ctx.expectedRestAmountAfterRollback.compareTo(aggregate.limits().get(0).rest()), "redis.limit.rest"),
                    () -> assertEquals(0, ctx.expectedSpentAmountAfterRollback.compareTo(aggregate.limits().get(0).spent()), "redis.limit.spent"),
                    () -> assertEquals(0, ctx.limitAmount.compareTo(aggregate.limits().get(0).amount()), "redis.limit.amount")
            );
        });
    }
}
