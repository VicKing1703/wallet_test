package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Проверяет роллбэк ставки, вытесненной из Redis после переполнения кеша.
 *
 * <p><b>Идея теста:</b>
 * Совершить набор ставок, вытеснить одну из них из Redis и убедиться, что API корректно обрабатывает роллбэк
 * по данным «холодного» хранилища.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Массовые ставки:</b>
 *     <p><b>Что проверяем:</b> корректность ответов {@code /bet} при достижении лимита кеша.</p>
 *     <p><b>Почему это важно:</b> гарантирует стабильность при высоких нагрузках.</p>
 *   </li>
 *   <li><b>Роллбэк вытесненной ставки:</b>
 *     <p><b>Что проверяем:</b> успешный ответ {@code /rollback} и восстановление баланса.</p>
 *     <p><b>Почему это важно:</b> подтверждает доступность данных вне Redis.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Каждая ставка завершается статусом {@code 200 OK} и уменьшает баланс.</li>
 *   <li>Роллбэк вытесненной ставки завершается статусом {@code 200 OK}.</li>
 *   <li>Баланс в ответе на роллбэк соответствует расчётному значению.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Позитивные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackDisplacedBetParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal BET_AMOUNT_BASE = new BigDecimal("1.00");
    private static final int MAX_GAMBLING_COUNT_IN_REDIS = 50;

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> transactionTypeAndAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(BET_AMOUNT_BASE),
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(BET_AMOUNT_BASE),
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(BET_AMOUNT_BASE),
                        NatsGamblingTransactionOperation.FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN
                )
        );
    }

    /**
     * Выполняет роллбэк ставки, вытесненной из Redis, и проверяет корректность ответа.
     *
     * @param betAmountParam сумма исходной ставки
     * @param typeParam тип исходной операции
     */
    @ParameterizedTest(name = "тип исходной транзакции = {1}, сумма = {0}")
    @MethodSource("transactionTypeAndAmountProvider")
    @DisplayName("Роллбэк транзакции, вытесненной из Redis")
    void testApiRollbackForDynamicallyIdentifiedDisplacedBet(
            BigDecimal betAmountParam,
            NatsGamblingTransactionOperation typeParam
    ) {
        final int requiredBetCount = MAX_GAMBLING_COUNT_IN_REDIS + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<BetRequestBody> madeBetsRequests = new ArrayList<>();
            String lastMadeBetTransactionId;
            BetRequestBody betToRollbackRequest;
            NatsMessage<NatsGamblingEventPayload> lastBetNatsEvent;
            BigDecimal currentCalculatedBalance;
            BigDecimal balanceFromApiAfterAllBets;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            ctx.currentCalculatedBalance = ctx.registeredPlayer.walletData().balance();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение " + requiredBetCount + " исходных транзакций", () -> {
            for (int i = 0; i < requiredBetCount; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == requiredBetCount - 1) {
                    ctx.lastMadeBetTransactionId = transactionId;
                }

                var betRequestBody = BetRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                        .amount(betAmountParam)
                        .transactionId(transactionId)
                        .type(typeParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(false)
                        .build();
                ctx.madeBetsRequests.add(betRequestBody);

                var currentBetNumber = i + 1;
                var currentTxId = transactionId;

                step("Совершение транзакции #" + currentBetNumber + " с ID: " + currentTxId, () -> {
                    var response = managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, betRequestBody),
                            betRequestBody);

                    ctx.currentCalculatedBalance = ctx.currentCalculatedBalance.subtract(betAmountParam);
                    assertAll(
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertEquals(currentTxId, response.getBody().transactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, ctx.currentCalculatedBalance.compareTo(response.getBody().balance()), "manager_api.body.balance")
                    );

                    if (currentBetNumber == requiredBetCount) {
                        ctx.balanceFromApiAfterAllBets = response.getBody().balance();
                    }
                });
            }

            assertEquals(requiredBetCount, ctx.madeBetsRequests.size(), "bet.list.size");
        });

        step("NATS: Ожидание события betted_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.lastBetNatsEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.lastMadeBetTransactionId)
                    .fetch();

            assertNotNull(ctx.lastBetNatsEvent, "nats.betted_from_gamble");
        });

        step("Redis: Определение вытесненной транзакции", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.lastBetNatsEvent.getSequence())
                    .fetch();

            var transactionIdsCurrentlyInRedis = aggregate.gambling().keySet();
            var displacedTransactionIds = ctx.madeBetsRequests.stream()
                    .map(BetRequestBody::getTransactionId)
                    .collect(Collectors.toCollection(HashSet::new));
            displacedTransactionIds.removeAll(transactionIdsCurrentlyInRedis);

            assertEquals(1, displacedTransactionIds.size(), "redis.displaced_transaction.expected_single");
            var displacedTxId = displacedTransactionIds.iterator().next();

            ctx.betToRollbackRequest = ctx.madeBetsRequests.stream()
                    .filter(betReq -> betReq.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));

            assertEquals(0, betAmountParam.compareTo(ctx.betToRollbackRequest.getAmount()), "bet.displaced.amount_mismatch");
            assertEquals(typeParam, ctx.betToRollbackRequest.getType(), "bet.displaced.type_mismatch");
        });

        step("Manager API: Роллбэк вытесненной транзакции", () -> {
            var rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(ctx.betToRollbackRequest.getAmount())
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(ctx.betToRollbackRequest.getTransactionId())
                    .roundId(ctx.betToRollbackRequest.getRoundId())
                    .roundClosed(true)
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .build();

            var expectedBalanceInApiResponse = ctx.balanceFromApiAfterAllBets.add(rollbackRequestBody.getAmount());

            var response = managerClient.rollback(
                    casinoId,
                    utils.createSignature(ApiEndpoints.ROLLBACK, rollbackRequestBody),
                    rollbackRequestBody);

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code"),
                    () -> assertEquals(rollbackRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.rollback.body.transactionId"),
                    () -> assertEquals(0, expectedBalanceInApiResponse.compareTo(response.getBody().balance()), "manager_api.rollback.body.balance")
            );
        });
    }
}
