package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверка уменьшения остатка лимита CasinoLoss при ставке в казино.
 *
 * <p>Тест создает лимит на проигрыш для разных периодов, затем выполняет
 * ставку одним из типов операции и убеждается, что в агрегате кошелька
 * значения {@code rest} и {@code spent} лимита корректно изменились.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового игрока.</li>
 *   <li><b>Создание игровой сессии:</b> через DefaultTestSteps.</li>
 *   <li><b>Установка лимита:</b> Public API {@code /profile/limit/casino-loss}.</li>
 *   <li><b>Проверка NATS:</b> событие {@code limit_changed_v2}.</li>
 *   <li><b>Основное действие:</b> ставка через Manager API.</li>
 *   <li><b>Проверка NATS:</b> событие {@code betted_from_gamble}.</li>
 *   <li><b>Проверка Redis:</b> поля лимита обновлены.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка лимита.</li>
 *   <li>Manager API: совершение ставки.</li>
 *   <li>NATS: события limit_changed_v2 и betted_from_gamble.</li>
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
class CasinoLossLimitWhenBetFromGambleParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> operationAndPeriodProvider() {
        return Stream.of(
                arguments(NatsGamblingTransactionOperation.BET, NatsLimitIntervalType.DAILY),
                arguments(NatsGamblingTransactionOperation.BET, NatsLimitIntervalType.WEEKLY),
                arguments(NatsGamblingTransactionOperation.BET, NatsLimitIntervalType.MONTHLY),
                arguments(NatsGamblingTransactionOperation.TIPS, NatsLimitIntervalType.DAILY),
                arguments(NatsGamblingTransactionOperation.TIPS, NatsLimitIntervalType.WEEKLY),
                arguments(NatsGamblingTransactionOperation.TIPS, NatsLimitIntervalType.MONTHLY),
                arguments(NatsGamblingTransactionOperation.FREESPIN, NatsLimitIntervalType.DAILY),
                arguments(NatsGamblingTransactionOperation.FREESPIN, NatsLimitIntervalType.WEEKLY),
                arguments(NatsGamblingTransactionOperation.FREESPIN, NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "тип = {0}, период = {1}")
    @MethodSource("operationAndPeriodProvider")
    @DisplayName("Изменение остатка CasinoLossLimit при совершении ставки в казино:")
    void test(
            NatsGamblingTransactionOperation type,
            NatsLimitIntervalType periodType
    ) {
        final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            BigDecimal limitAmount;
            BigDecimal betAmount;
            BigDecimal expectedRestAmountAfterBet;
            BigDecimal expectedSpentAmountAfterBet;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount =  new BigDecimal("150.12");
        ctx.betAmount = new BigDecimal("10.15");
        ctx.expectedSpentAmountAfterBet = ctx.betAmount;
        ctx.expectedRestAmountAfterBet = ctx.limitAmount.subtract(ctx.betAmount);

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
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .type(periodType)
                    .amount(ctx.limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_casino_loss_limit.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                var limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .fetch();

                assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
            });
        });

        step("Manager API: Совершение ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(ctx.betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(type)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");

            step("Sub-step NATS: Проверка поступления события betted_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.betRequestBody.getTransactionId())
                    .fetch();

                assertNotNull(ctx.betEvent, "nats.event.betted_from_gamble");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.betEvent.getSequence())
                    .fetch();

            assertAll(
                    () -> assertEquals(0, ctx.expectedRestAmountAfterBet.compareTo(aggregate.limits().get(0).getRest()), "redis.wallet.limit.rest"),
                    () -> assertEquals(0, ctx.expectedSpentAmountAfterBet.compareTo(aggregate.limits().get(0).getSpent()), "redis.wallet.limit.spent"),
                    () -> assertEquals(0, ctx.limitAmount.compareTo(aggregate.limits().get(0).getAmount()), "redis.wallet.limit.amount")
            );
        });
    }
}
