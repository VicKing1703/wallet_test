package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenWinParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmount = new BigDecimal("150.12");
    private static final BigDecimal winAmount = new BigDecimal("10.15");
    private static final String ZERO_UUID = new UUID(0L, 0L).toString();

    private static final EnumSet<NatsGamblingTransactionOperation> WIN_OPERATIONS = EnumSet.of(
            NatsGamblingTransactionOperation.WIN,
            NatsGamblingTransactionOperation.JACKPOT,
            NatsGamblingTransactionOperation.FREESPIN
    );

    private static String resolveTransactionTypeValue(NatsGamblingTransactionOperation operation) {
        return NatsGamblingTransactionType.valueOf("TYPE_" + operation.name()).getValue();
    }

    private static String expectedWinOperation(NatsGamblingTransactionOperation operation) {
        return WIN_OPERATIONS.contains(operation)
                ? NatsGamblingTransactionOperation.WIN.getValue()
                : operation.getValue();
    }

    static Stream<Arguments> operationAndPeriodProvider() {
        return Stream.of(
                arguments(NatsGamblingTransactionOperation.WIN, NatsLimitIntervalType.DAILY),
                arguments(NatsGamblingTransactionOperation.WIN, NatsLimitIntervalType.WEEKLY),
                arguments(NatsGamblingTransactionOperation.WIN, NatsLimitIntervalType.MONTHLY),
                arguments(NatsGamblingTransactionOperation.JACKPOT, NatsLimitIntervalType.DAILY),
                arguments(NatsGamblingTransactionOperation.JACKPOT, NatsLimitIntervalType.WEEKLY),
                arguments(NatsGamblingTransactionOperation.JACKPOT, NatsLimitIntervalType.MONTHLY),
                arguments(NatsGamblingTransactionOperation.FREESPIN, NatsLimitIntervalType.DAILY),
                arguments(NatsGamblingTransactionOperation.FREESPIN, NatsLimitIntervalType.WEEKLY),
                arguments(NatsGamblingTransactionOperation.FREESPIN, NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "тип = {0}, период = {1}")
    @MethodSource("operationAndPeriodProvider")
    @DisplayName("Отсутствие изменения остатка TurnoverLimit при получении выигрыша:")
    void testTurnoverLimitUnchangedOnWin(
            NatsGamblingTransactionOperation operationParam,
            NatsLimitIntervalType periodType
    ) {
        final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        final class TestContext extends BaseParameterizedTest {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            WinRequestBody winRequestBody;
            NatsMessage<NatsGamblingEventPayload> winEvent;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            BigDecimal limitAmount;
            BigDecimal expectedRestAmountAfterWin;
            BigDecimal expectedSpentAmountAfterWin;
            BigDecimal expectedPlayerBalanceAfterWin;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount = limitAmount;
        ctx.expectedSpentAmountAfterWin = BigDecimal.ZERO;
        ctx.expectedRestAmountAfterWin = ctx.limitAmount;
        ctx.expectedPlayerBalanceAfterWin = initialAdjustmentAmount.add(winAmount);

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
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .type(periodType)
                    .amount(ctx.limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

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

        step("Manager API: Начисление выигрыша", () -> {
            ctx.winRequestBody = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.winRequestBody),
                    ctx.winRequestBody);

            assertAll("manager_api.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertEquals(ctx.winRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedPlayerBalanceAfterWin.compareTo(response.getBody().balance()), "manager_api.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события won_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                ctx.winEvent = natsClient.expect(NatsGamblingEventPayload.class)
                        .from(subject)
                        .withType(NatsEventType.WON_FROM_GAMBLE.getHeaderValue())
                        .with("$.uuid", ctx.winRequestBody.getTransactionId())
                        .with("$.bet_uuid", ZERO_UUID)
                        .with("$.operation", expectedWinOperation(operationParam))
                        .with("$.type", resolveTransactionTypeValue(operationParam))
                        .fetch();
                assertNotNull(ctx.winEvent, "nats.won_from_gamble");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита и баланса в агрегате", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.winEvent.getSequence())
                    .fetch();

            assertAll("redis.wallet.limit_data_validation",
                    () -> assertEquals((int) ctx.winEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertFalse(aggregate.limits().isEmpty(), "redis.wallet.limits"),
                    () -> {
                        var turnoverLimitOpt = aggregate.limits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        periodType.getValue().equals(l.getIntervalType()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, ctx.expectedRestAmountAfterWin.compareTo(turnoverLimit.rest()), "redis.wallet.limit.rest");
                        assertEquals(0, ctx.expectedSpentAmountAfterWin.compareTo(turnoverLimit.spent()), "redis.wallet.limit.spent");
                        assertEquals(0, ctx.limitAmount.compareTo(turnoverLimit.amount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}
