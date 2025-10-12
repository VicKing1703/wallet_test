package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionDirection;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsMessageName;
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
import java.util.UUID;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет успешное выполнение роллбэка ставок для разных типов исходных операций.
 *
 * <p><b>Идея теста:</b>
 * Совершить ставку, выполнить роллбэк и убедиться, что все интеграции (HTTP API, Kafka, NATS, Redis, БД)
 * отражают возврат средств и связь с исходной транзакцией.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>REST API:</b>
 *     <p><b>Что проверяем:</b> ответы {@code /bet} и {@code /rollback} и расчет баланса.</p>
 *     <p><b>Почему это важно:</b> API — основной канал взаимодействия, ошибки недопустимы.</p>
 *   </li>
 *   <li><b>События:</b>
 *     <p><b>Что проверяем:</b> публикацию {@code rollbacked_from_gamble} и содержимое Kafka.</p>
 *     <p><b>Почему это важно:</b> downstream-сервисы зависят от корректности сообщений.</p>
 *   </li>
 *   <li><b>Хранилища:</b>
 *     <p><b>Что проверяем:</b> запись транзакции в БД и обновление кеша кошелька.</p>
 *     <p><b>Почему это важно:</b> баланс и лимиты игрока должны быть согласованными.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Ставка и роллбэк завершаются статусом {@code 200 OK}.</li>
 *   <li>Событие роллбэка фиксируется в NATS и Kafka с корректными атрибутами.</li>
 *   <li>Redis и БД отражают возврат средств и связь с исходной ставкой.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Позитивные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final String EXPECTED_CURRENCY_RATES = "1";

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> rollbackAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
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
     * Выполняет ставку и роллбэк для заданной суммы и типа операции.
     *
     * <p><b>Идея теста:</b>
     * Проверить, что возврат корректно восстанавливает баланс и фиксируется во всех интеграциях.</p>
     *
     * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
     * <ul>
     *   <li><b>Ставка через {@code /bet}:</b>
     *     <p><b>Что проверяем:</b> успешное списание средств и расчет баланса.</p>
     *     <p><b>Почему это важно:</b> исходная ставка задает основу для роллбэка.</p>
     *   </li>
     *   <li><b>Роллбэк через {@code /rollback}:</b>
     *     <p><b>Что проверяем:</b> возврат суммы и связь с исходной транзакцией.</p>
     *     <p><b>Почему это важно:</b> гарантия, что система корректно отменяет операции.</p>
     *   </li>
     * </ul>
     *
     * <p><b>Ожидаемые результаты:</b></p>
     * <ul>
     *   <li>API возвращает ожидаемые статусы и баланс игрока.</li>
     *   <li>События содержат корректные ссылки на исходную ставку.</li>
     *   <li>Хранилища отражают завершившийся роллбэк.</li>
     * </ul>
     *
     * @param rollbackAmountParam сумма исходной ставки и последующего роллбэка
     * @param operationTypeParam тип исходной транзакции (BET, TIPS, FREESPIN)
     */
    @ParameterizedTest(name = "Роллбэк транзакции типа {1} суммой {0}")
    @MethodSource("rollbackAmountProvider")
    @DisplayName("Получение роллбэка игроком в игровой сессии для разных сумм")
    void test(BigDecimal rollbackAmountParam, NatsGamblingTransactionOperation operationTypeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RollbackRequestBody rollbackRequestBody;
            NatsMessage<NatsGamblingEventPayload> rollbackEvent;
            BigDecimal adjustmentAmount;
            BigDecimal betAmount;
            BigDecimal rollbackAmount;
            String expectedCurrencyRates;
            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterRollback;
        }
        final TestContext ctx = new TestContext();

        ctx.rollbackAmount = rollbackAmountParam;
        ctx.betAmount = rollbackAmountParam;
        ctx.adjustmentAmount = INITIAL_ADJUSTMENT_AMOUNT;
        ctx.expectedCurrencyRates = EXPECTED_CURRENCY_RATES;
        ctx.expectedBalanceAfterBet = BigDecimal.ZERO
                .add(ctx.adjustmentAmount)
                .subtract(ctx.betAmount);
        ctx.expectedBalanceAfterRollback = BigDecimal.ZERO
                .add(ctx.adjustmentAmount)
                .subtract(ctx.betAmount)
                .add(ctx.rollbackAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(ctx.adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение исходной транзакции", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(ctx.betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationTypeParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("Manager API: Выполнение роллбэка транзакции", () -> {
            ctx.rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(ctx.rollbackAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(ctx.betRequestBody.getTransactionId())
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.rollback(
                    casinoId,
                    utils.createSignature(ApiEndpoints.ROLLBACK, ctx.rollbackRequestBody),
                    ctx.rollbackRequestBody);

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code"),
                    () -> assertEquals(ctx.rollbackRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.rollback.transaction_id"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterRollback.compareTo(response.getBody().balance()), "manager_api.rollback.balance")
            );
        });

        step("NATS: Проверка поступления события rollbacked_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.rollbackEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.ROLLBACKED_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.rollbackRequestBody.getTransactionId())
                    .fetch();

            assertNotNull(ctx.rollbackEvent, "nats.event.rollbacked_from_gamble");

            assertAll(
                    () -> assertEquals(ctx.rollbackRequestBody.getTransactionId(), ctx.rollbackEvent.getPayload().uuid(), "nats.rollback.uuid"),
                    () -> assertEquals(ctx.betRequestBody.getTransactionId(), ctx.rollbackEvent.getPayload().betUuid(), "nats.rollback.bet_uuid"),
                    () -> assertEquals(ctx.gameLaunchData.dbGameSession().getGameSessionUuid(), ctx.rollbackEvent.getPayload().gameSessionUuid(), "nats.rollback.game_session_uuid"),
                    () -> assertEquals(ctx.rollbackRequestBody.getRoundId(), ctx.rollbackEvent.getPayload().providerRoundId(), "nats.rollback.provider_round_id"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), ctx.rollbackEvent.getPayload().currency(), "nats.rollback.currency"),
                    () -> assertEquals(0, ctx.rollbackAmount.compareTo(ctx.rollbackEvent.getPayload().amount()), "nats.rollback.amount"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_ROLLBACK, ctx.rollbackEvent.getPayload().type(), "nats.rollback.type"),
                    () -> assertTrue(ctx.rollbackEvent.getPayload().providerRoundClosed(), "nats.rollback.round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, ctx.rollbackEvent.getPayload().message(), "nats.rollback.message_name"),
                    () -> assertNotNull(ctx.rollbackEvent.getPayload().createdAt(), "nats.rollback.created_at"),
                    () -> assertEquals(NatsGamblingTransactionDirection.DEPOSIT, ctx.rollbackEvent.getPayload().direction(), "nats.rollback.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.ROLLBACK, ctx.rollbackEvent.getPayload().operation(), "nats.rollback.operation"),
                    () -> assertEquals(platformNodeId, ctx.rollbackEvent.getPayload().nodeUuid(), "nats.rollback.node_uuid"),
                    () -> assertEquals(ctx.gameLaunchData.dbGameSession().getGameUuid(), ctx.rollbackEvent.getPayload().gameUuid(), "nats.rollback.game_uuid"),
                    () -> assertEquals(ctx.gameLaunchData.dbGameSession().getProviderUuid(), ctx.rollbackEvent.getPayload().providerUuid(), "nats.rollback.provider_uuid"),
                    () -> assertTrue(ctx.rollbackEvent.getPayload().wageredDepositInfo().isEmpty(), "nats.rollback.wagered_deposit_info"),
                    () -> assertEquals(0, ctx.rollbackAmount.compareTo(ctx.rollbackEvent.getPayload().currencyConversionInfo().gameAmount()), "nats.rollback.game_amount"),
                    () -> assertFalse(ctx.rollbackEvent.getPayload().currencyConversionInfo().gameCurrency().isEmpty(), "nats.rollback.game_currency"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), ctx.rollbackEvent.getPayload().currencyConversionInfo().currencyRates().get(0).baseCurrency(), "nats.rollback.base_currency"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), ctx.rollbackEvent.getPayload().currencyConversionInfo().currencyRates().get(0).quoteCurrency(), "nats.rollback.quote_currency"),
                    () -> assertEquals(ctx.expectedCurrencyRates, ctx.rollbackEvent.getPayload().currencyConversionInfo().currencyRates().get(0).value(), "nats.rollback.currency_rates"),
                    () -> assertNotNull(ctx.rollbackEvent.getPayload().currencyConversionInfo().currencyRates().get(0).updatedAt(), "nats.rollback.updated_at")
            );
        });

        step("DB Wallet: Проверка записи роллбэка в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient
                    .findTransactionByUuidOrFail(ctx.rollbackRequestBody.getTransactionId());

            assertNotNull(transaction, "db.transaction");

            assertAll(
                    () -> assertEquals(ctx.rollbackEvent.getPayload().uuid(), transaction.getUuid(), "db.transaction.uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), transaction.getPlayerUuid(), "db.transaction.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.transaction.date"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_ROLLBACK, transaction.getType(), "db.transaction.type"),
                    () -> assertEquals(NatsGamblingTransactionOperation.ROLLBACK, transaction.getOperation(), "db.transaction.operation"),
                    () -> assertEquals(ctx.rollbackEvent.getPayload().gameUuid(), transaction.getGameUuid(), "db.transaction.game_uuid"),
                    () -> assertEquals(ctx.rollbackEvent.getPayload().gameSessionUuid(), transaction.getGameSessionUuid(), "db.transaction.game_session_uuid"),
                    () -> assertEquals(ctx.rollbackEvent.getPayload().currency(), transaction.getCurrency(), "db.transaction.currency"),
                    () -> assertEquals(0, ctx.rollbackAmount.compareTo(transaction.getAmount()), "db.transaction.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.transaction.created_at"),
                    () -> assertEquals(ctx.rollbackEvent.getSequence(), transaction.getSeqnumber(), "db.transaction.seq_number"),
                    () -> assertEquals(ctx.rollbackEvent.getPayload().providerRoundClosed(), transaction.getProviderRoundClosed(), "db.transaction.provider_round_closed")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша после роллбэка", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    ctx.registeredPlayer.walletData().playerUUID());

            assertNotNull(threshold, "db.threshold");

            assertAll(
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, ctx.betAmount.negate().add(ctx.rollbackEvent.getPayload().amount()).compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("Kafka: Проверка сообщения о роллбэке в wallet.v8.projectionSource", () -> {
            var message = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.rollbackEvent.getSequence())
                    .fetch();

            assertNotNull(message, "kafka.message");
            assertTrue(utils.areEquivalent(message, ctx.rollbackEvent), "kafka.message.equivalent_to_nats");
        });

        step("Redis(Wallet): Проверка агрегата кошелька после роллбэка", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.rollbackEvent.getSequence())
                    .fetch();

            assertNotNull(aggregate, "redis.aggregate");

            assertAll(
                    () -> assertEquals(ctx.rollbackEvent.getSequence(), aggregate.lastSeqNumber(), "redis.aggregate.seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterRollback.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterRollback.compareTo(aggregate.availableWithdrawalBalance()), "redis.aggregate.available_balance"),
                    () -> assertTrue(aggregate.gambling().containsKey(ctx.rollbackEvent.getPayload().uuid()), "redis.aggregate.gambling_contains_uuid"),
                    () -> assertEquals(0, ctx.rollbackAmount.compareTo(aggregate.gambling().get(ctx.rollbackEvent.getPayload().uuid()).getAmount()), "redis.aggregate.gambling_amount"),
                    () -> assertNotNull(aggregate.gambling().get(ctx.rollbackEvent.getPayload().uuid()).getCreatedAt(), "redis.aggregate.gambling_created_at")
            );
        });
    }
}
