package com.uplatform.wallet_tests.tests.wallet.gambling.refund;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
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
 * Проверяет успешный рефанд ставки, вытесненной из Redis.
 *
 * <p><b>Идея теста:</b>
 * Совершить больше ставок, чем хранится в Redis, выявить вытесненную запись и убедиться, что рефанд обрабатывается корректно.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Менеджер API:</b>
 *     <p><b>Что проверяем:</b> последовательное выполнение {@code /bet} и {@code /refund}.</p>
 *     <p><b>Почему это важно:</b> API должен корректно работать даже при отсутствии данных в Redis.</p>
 *   </li>
 *   <li><b>Redis и NATS:</b>
 *     <p><b>Что проверяем:</b> определение вытесненной ставки и получение последовательности события.</p>
 *     <p><b>Почему это важно:</b> подтверждает консистентность между кешем и событиями.</p>
 *   </li>
 *   <li><b>Баланс игрока:</b>
 *     <p><b>Что проверяем:</b> расчет итогового баланса после рефанда.</p>
 *     <p><b>Почему это важно:</b> гарантирует корректную финансовую отчётность.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Рефанд вытесненной ставки завершается успехом.</li>
 *   <li>Баланс в ответе совпадает с расчетным.</li>
 *   <li>Тело ответа содержит идентификатор рефанда.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/refund")
@Suite("Позитивные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet7")
class RefundDisplacedBetParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("1000.00");
    private static final int MAX_GAMBLING_COUNT_IN_REDIS = 50;
    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> transactionTypeAndAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(new BigDecimal("1.00")),
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(new BigDecimal("1.00")),
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(new BigDecimal("1.00")),
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
     * Создает вытесненную ставку и проверяет, что рефанд проходит успешно.
     *
     * <p><b>Идея теста:</b>
     * Совершить {@code MAX_GAMBLING_COUNT_IN_REDIS + 1} ставок, определить ставку, отсутствующую в Redis,
     * и выполнить ее возврат.</p>
     *
     * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
     * <ul>
     *   <li><b>Redis:</b>
     *     <p><b>Что проверяем:</b> корректность определения вытесненной транзакции.</p>
     *     <p><b>Почему это важно:</b> тест подтверждает, что система может работать без кеша.</p>
     *   </li>
     *   <li><b>Refund API:</b>
     *     <p><b>Что проверяем:</b> статус {@code 200 OK} и баланс в ответе.</p>
     *     <p><b>Почему это важно:</b> гарантирует корректный расчет средств при восстановлении данных.</p>
     *   </li>
     * </ul>
     *
     * <p><b>Ожидаемые результаты:</b></p>
     * <ul>
     *   <li>Вытесненная ставка успешно идентифицируется.</li>
     *   <li>Рефанд завершается статусом {@code 200 OK}.</li>
     *   <li>Баланс в ответе соответствует расчетному значению.</li>
     * </ul>
     */
    @ParameterizedTest(name = "тип ставки = {1}, сумма ставки = {0}")
    @MethodSource("transactionTypeAndAmountProvider")
    @DisplayName("Рефанд ставки (разные типы и суммы), вытесненной из Redis")
    void testApiRefundForDynamicallyIdentifiedDisplacedBet(
            BigDecimal betAmountParam, NatsGamblingTransactionOperation typeParam) {
        final int currentTransactionCountToMake = MAX_GAMBLING_COUNT_IN_REDIS + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<BetRequestBody> madeBetsRequests = new ArrayList<>();
            String lastMadeBetTransactionId;
            BetRequestBody betToRefundRequest;
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

        step("Manager API: Совершение " + currentTransactionCountToMake + " ставок типа " + typeParam + " на сумму " + betAmountParam, () -> {
            for (int i = 0; i < currentTransactionCountToMake; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == currentTransactionCountToMake - 1) {
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

                step("Совершение ставки #" + currentBetNumber + " с ID: " + currentTxId, () -> {
                    var response = managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, betRequestBody),
                            betRequestBody);

                    assertNotNull(response.getBody(), "manager_api.bet.body_not_null");
                    ctx.currentCalculatedBalance = ctx.currentCalculatedBalance.subtract(betAmountParam);

                    assertAll("Проверка ответа API для ставки #" + currentBetNumber,
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertEquals(currentTxId, response.getBody().transactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, ctx.currentCalculatedBalance.compareTo(response.getBody().balance()), "manager_api.body.balance")
                    );

                    if (currentBetNumber == currentTransactionCountToMake) {
                        ctx.balanceFromApiAfterAllBets = response.getBody().balance();
                    }
                });
            }

            assertEquals(currentTransactionCountToMake, ctx.madeBetsRequests.size(), "bet.list.size");
        });

        step("NATS: Ожидание NATS-события betted_from_gamble для последней ставки", () -> {
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

        step("Redis: Определение вытесненной ставки", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.lastBetNatsEvent.getSequence())
                    .fetch();

            var gamblingTransactionsInRedis = aggregate.gambling();
            var transactionIdsCurrentlyInRedis = gamblingTransactionsInRedis.keySet();
            var displacedTransactionIds = ctx.madeBetsRequests.stream()
                    .map(BetRequestBody::getTransactionId).collect(Collectors.toCollection(HashSet::new));
            displacedTransactionIds.removeAll(transactionIdsCurrentlyInRedis);
            assertEquals(1, displacedTransactionIds.size(), "redis.displaced_transaction.expected_single");
            var displacedTxId = displacedTransactionIds.iterator().next();

            ctx.betToRefundRequest = ctx.madeBetsRequests.stream()
                    .filter(betReq -> betReq.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));

            assertEquals(0, betAmountParam.compareTo(ctx.betToRefundRequest.getAmount()), "bet.displaced.amount_mismatch");
        });

        step("Manager API: Рефанд вытесненной ставки", () -> {

            RefundRequestBody refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(ctx.betToRefundRequest.getAmount())
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.betToRefundRequest.getTransactionId())
                    .roundId(ctx.betToRefundRequest.getRoundId())
                    .roundClosed(true)
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .build();

            var expectedBalanceInApiResponse = ctx.balanceFromApiAfterAllBets.add(refundRequestBody.getAmount());

            var response = managerClient.refund(
                    casinoId,
                    utils.createSignature(ApiEndpoints.REFUND, refundRequestBody),
                    refundRequestBody);

            assertAll("Проверка ответа API на рефанд вытесненной ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code"),
                    () -> assertEquals(refundRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.refund.body.transactionId"),
                    () -> assertEquals(0, expectedBalanceInApiResponse.compareTo(response.getBody().balance()), "manager_api.refund.body.balance")
            );
        });
    }
}
