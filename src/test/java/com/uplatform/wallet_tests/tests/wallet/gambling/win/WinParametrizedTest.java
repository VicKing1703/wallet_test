package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий функциональность получения выигрышей в системе Wallet для азартных игр.
 *
 * <p><b>Идея теста:</b>
 * Отследить полный жизненный цикл операции {@code POST /win} — от вызова Manager API до консистентности
 * данных в NATS, Kafka, базе Wallet и Redis. Каждая итерация выполняется в изолированном окружении
 * (новый игрок и игровая сессия), что исключает влияние параллельных запусков.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Целостность Manager API:</b>
 *     <p><b>Что проверяем:</b> Возврат статуса {@link HttpStatus#OK}, корректного {@code transactionId}
 *     и ожидаемого баланса.</p>
 *     <p><b>Почему это важно:</b> Это основной контракт с игровыми провайдерами; ошибка ведет к потере
 *     выигрышей игроков и некорректным расчетам.</p>
 *   </li>
 *   <li><b>Событийная шина и база данных:</b>
 *     <p><b>Что проверяем:</b> Появление события {@code won_from_gamble} в NATS и запись в
 *     {@code gambling_projection_transaction_history}/{@code player_threshold_win}.</p>
 *     <p><b>Почему это важно:</b> Эти системы питают отчетность и лимиты — рассинхрон приводит к бизнес-рискам.</p>
 *   </li>
 *   <li><b>Согласованность кэшей и Kafka:</b>
 *     <p><b>Что проверяем:</b> Обновление агрегата кошелька в Redis и идентичность сообщения в Kafka.</p>
 *     <p><b>Почему это важно:</b> Эти данные отображаются пользователям и аналитике, поэтому должны совпадать</p>
 *     <p>с первичными источниками.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Создать игрока с балансом {@link #INITIAL_ADJUSTMENT_AMOUNT} и игровую сессию.</li>
 *   <li>Совершить ставку и зафиксировать баланс после ставки.</li>
 *   <li>Отправить запрос {@code /win} с параметрами набора данных.</li>
 *   <li>Проверить события в NATS и Kafka, записи в БД и состояние Redis.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает корректный статус и тело ответа.</li>
 *   <li>Событие {@code won_from_gamble} содержит ожидаемые значения и совпадает с БД.</li>
 *   <li>Баланс в Redis и сообщение Kafka соответствуют ответу API.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/win")
@Suite("Позитивные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class WinParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final String EXPECTED_CURRENCY_RATES = "1";

    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> winAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.WIN,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.WIN,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.JACKPOT,
                        NatsGamblingTransactionType.TYPE_JACKPOT
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.JACKPOT,
                        NatsGamblingTransactionType.TYPE_JACKPOT
                )
        );
    }

    /**
     * @param betAmountParam Сумма ставки, сгенерированная для текущего теста.
     * @param winAmountParam Сумма выигрыша, которая может быть как ненулевой (случайно сгенерированной),
     *                      так и нулевой в зависимости от сценария теста.
     * @param operationParam Тип операции выигрыша (WIN, FREESPIN или JACKPOT) для запроса и проверки.
     * @param transactionTypeParam Ожидаемый тип транзакции в событии NATS для соответствующей проверки.
     */
    @MethodSource("winAmountProvider")
    @ParameterizedTest(name = "тип = {2} и сумма = {1}")
    @DisplayName("Получение выигрыша:")
    void test(
            BigDecimal betAmountParam,
            BigDecimal winAmountParam,
            NatsGamblingTransactionOperation operationParam,
            NatsGamblingTransactionType transactionTypeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            WinRequestBody winRequestBody;
            NatsMessage<NatsGamblingEventPayload> winEvent;
            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterWin;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedBalanceAfterBet = BigDecimal.ZERO.add(INITIAL_ADJUSTMENT_AMOUNT).subtract(betAmountParam);
        ctx.expectedBalanceAfterWin = ctx.expectedBalanceAfterBet.add(winAmountParam);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Совершение ставки", () -> {
            var request = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();
            ctx.betRequestBody = request;

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, request),
                    request);

            assertAll("Проверка статус-кода и тела ответа ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.bet.body_not_null"),
                    () -> assertEquals(request.getTransactionId(), response.getBody().transactionId(), "manager_api.bet.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterBet.compareTo(response.getBody().balance()), "manager_api.bet.body.balance")
            );
        });

        step("Manager API: Получение выигрыша", () -> {
            ctx.winRequestBody = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.winRequestBody),
                    ctx.winRequestBody);

            assertAll("Проверка статус-кода и тела ответа выигрыша",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.win.body_not_null"),
                    () -> assertEquals(ctx.winRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.win.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(response.getBody().balance()), "manager_api.win.body.balance")
            );
        });

        step("NATS: Проверка поступления события won_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.winEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.WON_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.winRequestBody.getTransactionId())
                    .fetch();

            var winRequest = ctx.winRequestBody;
            var winEventPayload = ctx.winEvent.getPayload();
            var session = ctx.gameLaunchData.dbGameSession();
            var player = ctx.registeredPlayer.walletData();

            assertAll("Проверка основных полей NATS payload",
                    () -> assertEquals(winRequest.getTransactionId(), winEventPayload.uuid(), "nats.payload.uuid"),
                    () -> assertEquals(new UUID(0L, 0L).toString(), winEventPayload.betUuid(), "nats.payload.bet_uuid"),
                    () -> assertEquals(session.getGameSessionUuid(), winEventPayload.gameSessionUuid(), "nats.payload.game_session_uuid"),
                    () -> assertEquals(winRequest.getRoundId(), winEventPayload.providerRoundId(), "nats.payload.provider_round_id"),
                    () -> assertEquals(player.currency(), winEventPayload.currency(), "nats.payload.currency"),
                    () -> assertEquals(0, winAmountParam.compareTo(winEventPayload.amount()), "nats.payload.amount"),
                    () -> assertEquals(transactionTypeParam, winEventPayload.type(), "nats.payload.type"),
                    () -> assertTrue(winEventPayload.providerRoundClosed(), "nats.payload.provider_round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, winEventPayload.message(), "nats.payload.message"),
                    () -> assertNotNull(winEventPayload.createdAt(), "nats.payload.created_at"),
                    () -> assertEquals(NatsTransactionDirection.DEPOSIT, winEventPayload.direction(), "nats.payload.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.WIN, winEventPayload.operation(), "nats.payload.operation"),
                    () -> assertEquals(platformNodeId, winEventPayload.nodeUuid(), "nats.payload.node_uuid"),
                    () -> assertEquals(session.getGameUuid(), winEventPayload.gameUuid(), "nats.payload.game_uuid"),
                    () -> assertEquals(session.getProviderUuid(), winEventPayload.providerUuid(), "nats.payload.provider_uuid"),
                    () -> assertTrue(winEventPayload.wageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info")
            );

            var conversionInfo = winEventPayload.currencyConversionInfo();
            var currencyRates = conversionInfo.currencyRates().get(0);

            assertAll("Проверка полей внутри currency_conversion_info NATS payload",
                    () -> assertEquals(0, winAmountParam.compareTo(conversionInfo.gameAmount()), "currency_conversion_info.game_amount"),
                    () -> assertEquals(player.currency(), conversionInfo.gameCurrency(), "currency_conversion_info.game_currency"),
                    () -> assertEquals(player.currency(), currencyRates.baseCurrency(), "currency_conversion_info.currency_rates.base_currency"),
                    () -> assertEquals(player.currency(), currencyRates.quoteCurrency(), "currency_conversion_info.currency_rates.quote_currency"),
                    () -> assertEquals(EXPECTED_CURRENCY_RATES, currencyRates.value(), "currency_conversion_info.currency_rates.value"),
                    () -> assertNotNull(currencyRates.updatedAt(), "currency_conversion_info.currency_rates.updated_at")
            );
        });

        step("DB Wallet: Проверка записи истории ставок в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient.findTransactionByUuidOrFail(ctx.winRequestBody.getTransactionId());
            var payload = ctx.winEvent.getPayload();

            assertAll("Проверка полей gambling_projection_transaction_history",
                    () -> assertEquals(payload.uuid(), transaction.getUuid(), "db.gpth.uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), transaction.getPlayerUuid(), "db.gpth.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.gpth.date"),
                    () -> assertEquals(payload.type(), transaction.getType(), "db.gpth.type"),
                    () -> assertEquals(payload.operation(), transaction.getOperation(), "db.gpth.operation"),
                    () -> assertEquals(payload.gameUuid(), transaction.getGameUuid(), "db.gpth.game_uuid"),
                    () -> assertEquals(payload.gameSessionUuid(), transaction.getGameSessionUuid(), "db.gpth.game_session_uuid"),
                    () -> assertEquals(payload.currency(), transaction.getCurrency(), "db.gpth.currency"),
                    () -> assertEquals(0, winAmountParam.compareTo(transaction.getAmount()), "db.gpth.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.gpth.created_at"),
                    () -> assertEquals(ctx.winEvent.getSequence(), transaction.getSeqnumber(), "db.gpth.seqnumber"),
                    () -> assertEquals(payload.providerRoundClosed(), transaction.getProviderRoundClosed(), "db.gpth.provider_round_closed"),
                    () -> assertEquals(payload.betUuid(), transaction.getBetUuid(), "db.gpth.bet_uuid")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var playerUuid = ctx.registeredPlayer.walletData().playerUUID();
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(playerUuid);
            BigDecimal expectedThresholdAmount = winAmountParam.subtract(betAmountParam);

            assertAll("Проверка полей player_threshold_win",
                    () -> assertEquals(playerUuid, threshold.getPlayerUuid(), "db.ptw.player_uuid"),
                    () -> assertEquals(0, expectedThresholdAmount.compareTo(threshold.getAmount()), "db.ptw.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.ptw.updated_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var walletUuid = ctx.registeredPlayer.walletData().walletUUID();
            int sequence = (int) ctx.winEvent.getSequence();
            var transactionUuid = ctx.winEvent.getPayload().uuid();

            var aggregate = redisWalletClient
                    .key(walletUuid)
                    .withAtLeast("LastSeqNumber", sequence)
                    .fetch();

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(sequence, aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(aggregate.balance()), "redis.wallet.balance"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(aggregate.availableWithdrawalBalance()), "redis.wallet.availableWithdrawalBalance"),
                    () -> assertTrue(aggregate.gambling().containsKey(transactionUuid), "redis.wallet.gambling.containsKey"),
                    () -> assertEquals(0, winAmountParam.compareTo(aggregate.gambling().get(transactionUuid).amount()), "redis.wallet.gambling.amount"),
                    () -> assertNotNull(aggregate.gambling().get(transactionUuid).createdAt(), "redis.wallet.gambling.createdAt")
            );
        });

        step("Kafka: Проверка поступления сообщения won_from_gamble в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.winEvent.getSequence())
                    .fetch();

            assertTrue(utils.areEquivalent(kafkaMessage, ctx.winEvent), "wallet.v8.projectionSource");
        });
    }
}
