package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
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
 * Сквозной интеграционный тест, верифицирующий корректность обработки позитивных сценариев совершения ставки ({@code POST /bet}).
 *
 * <p><b>Идея теста:</b>
 * Подтвердить полную сквозную консистентность данных при обработке одной из ключевых финансовых
 * операций — совершения ставки. Тест должен доказать, что успешный API-запрос инициирует корректную и атомарную
 * последовательность событий: от генерации сообщения в NATS до финального обновления состояния в персистентных (БД)
 * и кеширующих (Redis) хранилищах. Это гарантирует целостность финансового учета и синхронизацию состояния игрока
 * во всей распределенной системе.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Распространение события (Event Propagation):</b>
 *     <p><b>Что проверяем:</b> Корректную генерацию и передачу события {@code betted_from_gamble} через NATS,
 *     а также его последующую проекцию в Kafka-топик {@code wallet.v8.projectionSource}.
 *     <p><b>Почему это важно:</b> Это основной механизм межсервисного взаимодействия. Сбой на этом этапе приведет
 *     к рассинхронизации данных в системе (например, сервис отчетов не получит информацию о транзакции).
 *   </li>
 *   <li><b>Обновление состояния (State Update):</b>
 *     <p><b>Что проверяем:</b> Корректное изменение баланса игрока и сохранение транзакции в основной базе данных
 *     (таблица {@code gambling_projection_transaction_history}) и в кеше (агрегат кошелька в Redis).
 *     <p><b>Почему это важно:</b> Верификация данных одновременно в персистентном хранилище (гарантия сохранности)
 *     и в кеше (гарантия производительности и актуальности для быстрых чтений) подтверждает, что модель чтения (Redis)
 *     полностью соответствует модели записи (БД), что критично для финансовых систем.
 *   </li>
 *   <li><b>Побочные эффекты бизнес-логики (Business Logic Side-Effects):</b>
 *     <p><b>Что проверяем:</b> Обновление вспомогательных данных, таких как пороги выигрыша игрока
 *     (таблица {@code player_threshold_win}).
 *     <p><b>Почему это важно:</b> Это подтверждает, что система обрабатывает не только основную финансовую операцию,
 *     но и связанные с ней бизнес-процессы (например, для аналитики, сегментации или антифрод-систем).
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API-запрос на совершение ставки успешно обрабатывается (HTTP 200 OK) и возвращает актуальный баланс.</li>
 *   <li>В NATS публикуется событие {@code betted_from_gamble} с полностью корректным набором данных.</li>
 *   <li>В БД создается запись о транзакции в таблице {@code gambling_projection_transaction_history}.</li>
 *   <li>В БД обновляется запись о пороге выигрыша в таблице {@code player_threshold_win}.</li>
 *   <li>Агрегат кошелька в Redis обновляется: изменяется баланс, {@code lastSeqNumber} и добавляется транзакция в историю.</li>
 *   <li>В Kafka публикуется сообщение, полностью идентичное по содержанию NATS-событию.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/bet")
@Suite("Позитивные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class BetParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final String EXPECTED_CURRENCY_RATES = "1";

    private String casinoId;

    static Stream<Arguments> betAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        INITIAL_ADJUSTMENT_AMOUNT,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        INITIAL_ADJUSTMENT_AMOUNT,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        INITIAL_ADJUSTMENT_AMOUNT,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                )
        );
    }

    @ParameterizedTest(name = "тип = {2} и сумма = {0}")
    @MethodSource("betAmountProvider")
    @DisplayName("Совершение ставки:")
    void test(
            BigDecimal amountParam,
            NatsGamblingTransactionOperation operationParam,
            NatsGamblingTransactionType transactionTypeParam) {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            BigDecimal expectedBalance;
        }
        final TestContext ctx = new TestContext();
        ctx.expectedBalance = BigDecimal.ZERO.add(INITIAL_ADJUSTMENT_AMOUNT).subtract(amountParam);

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
                    .amount(amountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();
            ctx.betRequestBody = request;

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertEquals(request.getTransactionId(), response.getBody().transactionId(), "manager_api.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(response.getBody().balance()), "manager_api.body.balance")
            );
        });

        step("NATS: Проверка поступления события betted_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.betRequestBody.getTransactionId())
                    .fetch();

            var betRequest = ctx.betRequestBody;
            var betEvent = ctx.betEvent.getPayload();
            var session = ctx.gameLaunchData.dbGameSession();
            var player = ctx.registeredPlayer.walletData();
            assertAll("Проверка основных полей NATS payload",
                    () -> assertEquals(betRequest.getTransactionId(), betEvent.uuid(), "nats.payload.uuid"),
                    () -> assertEquals(new UUID(0L, 0L).toString(), betEvent.betUuid(), "nats.payload.bet_uuid"),
                    () -> assertEquals(session.getGameSessionUuid(), betEvent.gameSessionUuid(), "nats.payload.game_session_uuid"),
                    () -> assertEquals(betRequest.getRoundId(), betEvent.providerRoundId(), "nats.payload.provider_round_id"),
                    () -> assertEquals(player.currency(), betEvent.currency(), "nats.payload.currency"),
                    () -> assertEquals(0, amountParam.negate().compareTo(betEvent.amount()), "nats.payload.amount"),
                    () -> assertEquals(transactionTypeParam, betEvent.type(), "nats.payload.type"),
                    () -> assertFalse(betEvent.providerRoundClosed(), "nats.payload.provider_round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, betEvent.message(), "nats.payload.message"),
                    () -> assertNotNull(betEvent.createdAt(), "nats.payload.created_at"),
                    () -> assertEquals(NatsTransactionDirection.WITHDRAW, betEvent.direction(), "nats.payload.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.BET, betEvent.operation(), "nats.payload.operation"),
                    () -> assertEquals(platformNodeId, betEvent.nodeUuid(), "nats.payload.node_uuid"),
                    () -> assertEquals(session.getGameUuid(), betEvent.gameUuid(), "nats.payload.game_uuid"),
                    () -> assertEquals(session.getProviderUuid(), betEvent.providerUuid(), "nats.payload.provider_uuid"),
                    () -> assertTrue(betEvent.wageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info")
            );

            var conversionInfo = betEvent.currencyConversionInfo();
            var currencyRates = conversionInfo.currencyRates().get(0);
            assertAll("Проверка полей внутри currency_conversion_info NATS payload",
                    () -> assertEquals(0, amountParam.negate().compareTo(conversionInfo.gameAmount()), "currency_conversion_info.game_amount"),
                    () -> assertFalse(conversionInfo.gameCurrency().isEmpty(), "currency_conversion_info.game_currency"),
                    () -> assertEquals(player.currency(), currencyRates.baseCurrency(), "currency_conversion_info.currency_rates.base_currency"),
                    () -> assertEquals(player.currency(), currencyRates.quoteCurrency(), "currency_conversion_info.currency_rates.quote_currency"),
                    () -> assertEquals(EXPECTED_CURRENCY_RATES, currencyRates.value(), "currency_conversion_info.currency_rates.value"),
                    () -> assertNotNull(currencyRates.updatedAt(), "currency_conversion_info.currency_rates.updated_at")
            );
        });

        step("DB Wallet: Проверка записи истории ставок в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient.findTransactionByUuidOrFail(ctx.betRequestBody.getTransactionId());
            var payload = ctx.betEvent.getPayload();
            assertAll("Проверка полей gambling_projection_transaction_history",
                    () -> assertEquals(payload.uuid(), transaction.getUuid(), "db.gpth.uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), transaction.getPlayerUuid(), "db.gpth.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.gpth.date"),
                    () -> assertEquals(payload.type(), transaction.getType(), "db.gpth.type"),
                    () -> assertEquals(payload.operation(), transaction.getOperation(), "db.gpth.operation"),
                    () -> assertEquals(payload.gameUuid(), transaction.getGameUuid(), "db.gpth.game_uuid"),
                    () -> assertEquals(payload.gameSessionUuid(), transaction.getGameSessionUuid(), "db.gpth.game_session_uuid"),
                    () -> assertEquals(payload.currency(), transaction.getCurrency(), "db.gpth.currency"),
                    () -> assertEquals(0, amountParam.negate().compareTo(transaction.getAmount()), "db.gpth.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.gpth.created_at"),
                    () -> assertEquals(ctx.betEvent.getSequence(), transaction.getSeqnumber(), "db.gpth.seqnumber"),
                    () -> assertEquals(payload.providerRoundClosed(), transaction.getProviderRoundClosed(), "db.gpth.provider_round_closed"),
                    () -> assertEquals(payload.betUuid(), transaction.getBetUuid(), "db.gpth.bet_uuid")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var playerUuid = ctx.registeredPlayer.walletData().playerUUID();
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(playerUuid);
            assertAll("Проверка полей player_threshold_win",
                    () -> assertEquals(playerUuid, threshold.getPlayerUuid(), "db.ptw.player_uuid"),
                    () -> assertEquals(0, amountParam.negate().compareTo(threshold.getAmount()), "db.ptw.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.ptw.updated_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var walletUuid = ctx.registeredPlayer.walletData().walletUUID();
            int sequence = (int) ctx.betEvent.getSequence();
            var transactionUuid = ctx.betEvent.getPayload().uuid();

            var aggregate = redisWalletClient
                    .key(walletUuid)
                    .withAtLeast("LastSeqNumber", sequence)
                    .fetch();

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(sequence, aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.balance()), "redis.wallet.balance"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.availableWithdrawalBalance()), "redis.wallet.availableWithdrawalBalance"),
                    () -> assertTrue(aggregate.gambling().containsKey(transactionUuid), "redis.wallet.gambling.containsKey"),
                    () -> assertEquals(0, amountParam.negate().compareTo(aggregate.gambling().get(transactionUuid).amount()), "redis.wallet.gambling.amount"),
                    () -> assertNotNull(aggregate.gambling().get(transactionUuid).createdAt(), "redis.wallet.gambling.createdAt")
            );
        });

        step("Kafka: Проверка поступления сообщения betted_from_gamble в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.betEvent.getSequence())
                    .fetch();

            assertTrue(utils.areEquivalent(kafkaMessage, ctx.betEvent), "wallet.v8.projectionSource");
        });
    }
}
