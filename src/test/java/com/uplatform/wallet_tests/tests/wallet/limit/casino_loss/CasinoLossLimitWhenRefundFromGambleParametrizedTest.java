package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
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
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Проверка уменьшения остатка лимита CasinoLoss при рефанде в казино.
 *
 * <p>Тест создает лимит на проигрыш для разных периодов, совершает ставку и
 * затем рефанд. Проверяется, что значения {@code rest} и {@code spent} лимита
 * изменились корректно.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового игрока.</li>
 *   <li><b>Создание игровой сессии:</b> через DefaultTestSteps.</li>
 *   <li><b>Установка лимита:</b> Public API {@code /profile/limit/casino-loss}.</li>
 *   <li><b>Проверка NATS:</b> событие {@code limit_changed_v2}.</li>
 *   <li><b>Основное действие:</b> ставка и рефанд через Manager API.</li>
 *   <li><b>Проверка NATS:</b> событие {@code refunded_from_gamble}.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька обновлен.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка лимита.</li>
 *   <li>Manager API: ставка и рефанд.</li>
 *   <li>NATS: события limit_changed_v2 и refunded_from_gamble.</li>
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
class CasinoLossLimitWhenRefundFromGambleParametrizedTest extends BaseParameterizedTest {

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Изменение остатка CasinoLossLimit при получении рефанда в казино")
    void test(NatsLimitIntervalType periodType) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RefundRequestBody refundRequestBody;
            NatsMessage<NatsGamblingEventPayload> refundEvent;
            BigDecimal limitAmount;
            BigDecimal betAmount;
            BigDecimal refundAmount;
            BigDecimal expectedRestAmountAfterRefund;
            BigDecimal expectedSpentAmountAfterRefund;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount =  new BigDecimal("150.12");
        ctx.betAmount = new BigDecimal("10.15");
        ctx.refundAmount = ctx.betAmount;
        ctx.expectedSpentAmountAfterRefund = BigDecimal.ZERO;
        ctx.expectedRestAmountAfterRefund = ctx.limitAmount;

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

                BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                        NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader);

                var limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

                assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
            });
        });

        step("Manager API: Совершение ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
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

        step("Manager API: Получение рефанда", () -> {
            ctx.refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(ctx.refundAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.betRequestBody.getTransactionId())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.refund(
                    casinoId,
                    utils.createSignature(ApiEndpoints.REFUND, ctx.refundRequestBody),
                    ctx.refundRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code");

            step("Sub-step NATS: Проверка поступления события refunded_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.REFUNDED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.refundRequestBody.getTransactionId().equals(payload.getUuid());

                ctx.refundEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

                assertNotNull(ctx.refundEvent, "nats.event.refunded_from_gamble");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().walletUUID(),
                    (int) ctx.refundEvent.getSequence());

            assertAll(
                    () -> assertEquals(0, ctx.expectedRestAmountAfterRefund.compareTo(aggregate.limits().get(0).getRest()), "redis.wallet.limit.rest"),
                    () -> assertEquals(0, ctx.expectedSpentAmountAfterRefund.compareTo(aggregate.limits().get(0).getSpent()), "redis.wallet.limit.spent"),
                    () -> assertEquals(0, ctx.limitAmount.compareTo(aggregate.limits().get(0).getAmount()), "redis.wallet.limit.amount")
            );
        });
    }
}
