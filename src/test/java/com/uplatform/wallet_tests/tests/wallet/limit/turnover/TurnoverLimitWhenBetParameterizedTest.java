package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.testing.multisource.api.nats.dto.NatsMessage;
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
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий изменение лимита на оборот средств в агрегате игрока
 * при совершении ставок различных типов (BET, TIPS, FREESPIN) в гемблинге.
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API: Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *   <li>REST API: Совершение ставки через Manager API ({@code /bet}).</li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Передача события {@code betted_from_gamble} через NATS при совершении ставки.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Обновление данных лимита и баланса игрока в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>}).</li>
 * </ul>
 *
 * <p><b>Проверяемые типы ставок ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code BET} - обычная ставка.</li>
 *   <li>{@code TIPS} - чаевые.</li>
 *   <li>{@code FREESPIN} - бесплатные вращения.
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenBetParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmount = new BigDecimal("150.12");
    private static final BigDecimal betAmount = new BigDecimal("10.15");
    private static final String ZERO_UUID = new UUID(0L, 0L).toString();
    private static final EnumSet<NatsGamblingTransactionOperation> BETTING_OPERATIONS = EnumSet.of(
            NatsGamblingTransactionOperation.BET,
            NatsGamblingTransactionOperation.TIPS,
            NatsGamblingTransactionOperation.FREESPIN
    );

    private static String expectedBetOperation(NatsGamblingTransactionOperation operation) {
        return BETTING_OPERATIONS.contains(operation)
                ? NatsGamblingTransactionOperation.BET.getValue()
                : operation.getValue();
    }

    private static String resolveTransactionTypeValue(NatsGamblingTransactionOperation operation) {
        return NatsGamblingTransactionType.valueOf("TYPE_" + operation.name()).getValue();
    }

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

    /**
     * @param operationParam Тип операции ставки (для запроса и проверки NATS).
     * @param periodType     Период действия лимита.
     */
    @ParameterizedTest(name = "тип = {0}, период = {1}")
    @MethodSource("operationAndPeriodProvider")
    @DisplayName("Изменение остатка TurnoverLimit при совершении ставки:")
    void testTurnoverLimitChangeOnBet(
            NatsGamblingTransactionOperation operationParam,
            NatsLimitIntervalType periodType
    ) {
        final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            BigDecimal limitAmount;
            BigDecimal expectedRestAmountAfterBet;
            BigDecimal expectedSpentAmountAfterBet;
            BigDecimal expectedPlayerBalanceAfterBet;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount = limitAmount;
        ctx.expectedSpentAmountAfterBet = betAmount;
        ctx.expectedRestAmountAfterBet = ctx.limitAmount.subtract(betAmount);
        ctx.expectedPlayerBalanceAfterBet = initialAdjustmentAmount.subtract(betAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var request = SetTurnoverLimitRequest.builder()
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .type(periodType)
                    .amount(ctx.limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    ctx.registeredPlayer.authorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.walletData().playerUUID(),
                        ctx.registeredPlayer.walletData().walletUUID());

                var expectedAmount = new BigDecimal(request.amount()).stripTrailingZeros().toPlainString();

                ctx.limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                        .from(subject)
                        .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                        .with("$.event_type", NatsLimitEventType.CREATED.getValue())
                        .with("$.limits[0].limit_type", NatsLimitType.TURNOVER_FUNDS.getValue())
                        .with("$.limits[0].interval_type", periodType.getValue())
                        .with("$.limits[0].currency_code", request.currency())
                        .with("$.limits[0].amount", expectedAmount)
                        .with("$.limits[0].status", true)
                        .fetch();

                assertNotNull(ctx.limitCreateEvent, "nats.limit_changed_v2_event");
            });
        });

        step("Manager API: Совершение ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertAll("manager_api.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertEquals(ctx.betRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedPlayerBalanceAfterBet.compareTo(response.getBody().balance()), "manager_api.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события betted_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.walletData().playerUUID(),
                        ctx.registeredPlayer.walletData().walletUUID());

                ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
                        .from(subject)
                        .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
                        .with("$.uuid", ctx.betRequestBody.getTransactionId())
                        .with("$.bet_uuid", ZERO_UUID)
                        .with("$.operation", expectedBetOperation(operationParam))
                        .with("$.type", resolveTransactionTypeValue(operationParam))
                        .fetch();

                assertNotNull(ctx.betEvent, "nats.betted_from_gamble");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита и баланса в агрегате", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.betEvent.getSequence())
                    .fetch();

            assertAll("redis.wallet.limit_data_validation",
                    () -> assertEquals((int) ctx.betEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertFalse(aggregate.limits().isEmpty(), "redis.wallet.limits"),
                    () -> {
                        var turnoverLimitOpt = aggregate.limits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        periodType.getValue().equals(l.getIntervalType()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, ctx.expectedRestAmountAfterBet.compareTo(turnoverLimit.rest()), "redis.wallet.limit.rest");
                        assertEquals(0, ctx.expectedSpentAmountAfterBet.compareTo(turnoverLimit.spent()), "redis.wallet.limit.spent");
                        assertEquals(0, ctx.limitAmount.compareTo(turnoverLimit.amount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}
