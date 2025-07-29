package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
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
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверка уменьшения остатка лимита CasinoLoss при турнирном выигрыше.
 *
 * <p>Тест создает лимит на проигрыш для разных периодов и производит турнирный
 * выигрыш, после чего проверяет изменения значений {@code rest} и
 * {@code spent} лимита в агрегате кошелька.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового игрока.</li>
 *   <li><b>Создание игровой сессии:</b> через DefaultTestSteps.</li>
 *   <li><b>Установка лимита:</b> Public API {@code /profile/limit/casino-loss}.</li>
 *   <li><b>Проверка NATS:</b> событие {@code limit_changed_v2}.</li>
 *   <li><b>Основное действие:</b> получение выигрыша в турнире через Manager API.</li>
 *   <li><b>Проверка NATS:</b> событие {@code tournament_won_from_gamble}.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька обновлен.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка лимита.</li>
 *   <li>Manager API: турнирный выигрыш.</li>
 *   <li>NATS: события limit_changed_v2 и tournament_won_from_gamble.</li>
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
class CasinoLossLimitWhenTournamentFromGambleParametrizedTest extends BaseParameterizedTest {

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Изменение остатка CasinoLossLimit при получении турнирного выигрыша в казино")
    void shouldRejectBetWhenGamblingDisabled(NatsLimitIntervalType periodType) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequestBody;
            NatsMessage<NatsGamblingEventPayload> tournamentEvent;
            BigDecimal limitAmount;
            BigDecimal tournamentAmount;
            BigDecimal expectedRestAmountAfterTournament;
            BigDecimal expectedSpentAmountAfterTournament;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount =  new BigDecimal("150.12");
        ctx.tournamentAmount = new BigDecimal("10.15");
        ctx.expectedSpentAmountAfterTournament = ctx.tournamentAmount.negate();
        ctx.expectedRestAmountAfterTournament = ctx.limitAmount.add(ctx.tournamentAmount);

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
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
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
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                        NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader);

                var limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

                assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
            });
        });

        step("Manager API: Получение выигрыша в турнире", () -> {
            ctx.tournamentRequestBody = TournamentRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(ctx.tournamentAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .roundId(UUID.randomUUID().toString())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.tournamentRequestBody),
                    ctx.tournamentRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code");

            step("Sub-step NATS: Проверка поступления события tournament_won_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.tournamentRequestBody.getTransactionId().equals(payload.getUuid());

                ctx.tournamentEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

                assertNotNull(ctx.tournamentEvent, "nats.event.tournament_started");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.tournamentEvent.getSequence());

            assertAll(
                    () -> assertEquals(0, ctx.expectedRestAmountAfterTournament.compareTo(aggregate.getLimits().get(0).getRest()), "redis.wallet.limit.rest"),
                    () -> assertEquals(0, ctx.expectedSpentAmountAfterTournament.compareTo(aggregate.getLimits().get(0).getSpent()), "redis.wallet.limit.spent"),
                    () -> assertEquals(0, ctx.limitAmount.compareTo(aggregate.getLimits().get(0).getAmount()), "redis.wallet.limit.amount")
            );
        });
    }
}
