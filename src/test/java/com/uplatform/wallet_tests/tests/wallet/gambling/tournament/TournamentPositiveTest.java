package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsMessageName;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsTransactionDirection;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет успешное начисление турнирного выигрыша и консистентность данных во всех слоях системы.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить, что позитивный сценарий {@code POST /tournament} корректно обновляет баланс игрока
 * и распространяет событие во все подключенные хранилища (NATS, Kafka, базы данных, Redis).
 * Это гарантирует целостность финансовых данных после начисления выигрыша.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>API-ответ и баланс:</b>
 *     <p><b>Что проверяем:</b> Успешный {@link HttpStatus#OK} и корректный расчет баланса после выигрыша.</p>
 *     <p><b>Почему это важно:</b> От API зависит первичная фиксация финансового результата игрока.</p>
 *   </li>
 *   <li><b>Событие в NATS и Kafka:</b>
 *     <p><b>Что проверяем:</b> Согласованность данных между NATS-событием и Kafka-проекцией.</p>
 *     <p><b>Почему это важно:</b> Эти каналы питают downstream-сервисы (аналитику, отчеты),
 *     поэтому они должны получать идентичный payload.</p>
 *   </li>
 *   <li><b>Персистентные хранилища:</b>
 *     <p><b>Что проверяем:</b> Обновление записей в таблицах истории и порогов, а также агрегата кошелька в Redis.</p>
 *     <p><b>Почему это важно:</b> Согласованность между кешем и БД обеспечивает надежность финансового учета.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Зарегистрировать игрока и создать игровую сессию.</li>
 *   <li>Начислить турнирный выигрыш через Manager API.</li>
 *   <li>Проверить событие в NATS и сообщение в Kafka.</li>
 *   <li>Убедиться в корректных данных в БД и Redis.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает корректный {@link HttpStatus#OK} и новый баланс.</li>
 *   <li>NATS и Kafka содержат идентичные данные о выигрыше.</li>
 *   <li>История транзакций, пороги и агрегат кошелька обновлены ожидаемыми значениями.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Позитивные сценарии: /tournament")
@Tag("Gambling")
@Tag("Wallet")
class TournamentPositiveTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final String EXPECTED_CURRENCY_RATES = "1";

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    @Test
    @DisplayName("Получение выигрыша в турнире игроком в игровой сессии")
    void shouldAwardTournamentWinAndVerify() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final BigDecimal tournamentAmount = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequestBody;
            NatsMessage<NatsGamblingEventPayload> tournamentEvent;
            BigDecimal expectedBalanceAfterTournament;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedBalanceAfterTournament = BigDecimal.ZERO
                .add(INITIAL_ADJUSTMENT_AMOUNT)
                .add(tournamentAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии и проверка в БД", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            ctx.tournamentRequestBody = TournamentRequestBody.builder()
                    .amount(tournamentAmount)
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(ctx.gameLaunchData.dbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.tournamentRequestBody),
                    ctx.tournamentRequestBody);

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.tournament.body_not_null"),
                    () -> assertEquals(ctx.tournamentRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.tournament.transaction_id"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(response.getBody().balance()), "manager_api.tournament.balance")
            );
        });

        step("NATS: Проверка поступления события tournament_won_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.tournamentEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.tournamentRequestBody.getTransactionId())
                    .fetch();

            assertAll(
                    () -> assertEquals(ctx.tournamentRequestBody.getTransactionId(), ctx.tournamentEvent.getPayload().uuid(), "nats.payload.uuid"),
                    () -> assertEquals(new UUID(0L, 0L).toString(), ctx.tournamentEvent.getPayload().betUuid(), "nats.payload.bet_uuid"),
                    () -> assertEquals(ctx.tournamentRequestBody.getSessionToken(), ctx.tournamentEvent.getPayload().gameSessionUuid(), "nats.payload.game_session_uuid"),
                    () -> assertEquals(ctx.tournamentRequestBody.getRoundId(), ctx.tournamentEvent.getPayload().providerRoundId(), "nats.payload.provider_round_id"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), ctx.tournamentEvent.getPayload().currency(), "nats.payload.currency"),
                    () -> assertEquals(0, tournamentAmount.compareTo(ctx.tournamentEvent.getPayload().amount()), "nats.payload.amount"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_TOURNAMENT, ctx.tournamentEvent.getPayload().type(), "nats.payload.type"),
                    () -> assertFalse(ctx.tournamentEvent.getPayload().providerRoundClosed(), "nats.payload.provider_round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, ctx.tournamentEvent.getPayload().message(), "nats.payload.message"),
                    () -> assertNotNull(ctx.tournamentEvent.getPayload().createdAt(), "nats.payload.created_at"),
                    () -> assertEquals(NatsTransactionDirection.DEPOSIT, ctx.tournamentEvent.getPayload().direction(), "nats.payload.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.TOURNAMENT, ctx.tournamentEvent.getPayload().operation(), "nats.payload.operation"),
                    () -> assertEquals(platformNodeId, ctx.tournamentEvent.getPayload().nodeUuid(), "nats.payload.node_uuid"),
                    () -> assertEquals(ctx.tournamentRequestBody.getGameUuid(), ctx.tournamentEvent.getPayload().gameUuid(), "nats.payload.game_uuid"),
                    () -> assertEquals(ctx.tournamentRequestBody.getProviderUuid(), ctx.tournamentEvent.getPayload().providerUuid(), "nats.payload.provider_uuid"),
                    () -> assertTrue(ctx.tournamentEvent.getPayload().wageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info"),
                    () -> assertEquals(0, tournamentAmount.compareTo(ctx.tournamentEvent.getPayload().currencyConversionInfo().gameAmount()), "nats.payload.currency_conversion.game_amount"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), ctx.tournamentEvent.getPayload().currencyConversionInfo().gameCurrency(), "nats.payload.currency_conversion.game_currency"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), ctx.tournamentEvent.getPayload().currencyConversionInfo().currencyRates().get(0).baseCurrency(), "nats.payload.currency_conversion.base_currency"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), ctx.tournamentEvent.getPayload().currencyConversionInfo().currencyRates().get(0).quoteCurrency(), "nats.payload.currency_conversion.quote_currency"),
                    () -> assertEquals(EXPECTED_CURRENCY_RATES, ctx.tournamentEvent.getPayload().currencyConversionInfo().currencyRates().get(0).value(), "nats.payload.currency_conversion.rate_value"),
                    () -> assertNotNull(ctx.tournamentEvent.getPayload().currencyConversionInfo().currencyRates().get(0).updatedAt(), "nats.payload.currency_conversion.updated_at")
            );
        });

        step("DB Wallet: Проверка записи истории ставок в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient.
                    findTransactionByUuidOrFail(ctx.tournamentRequestBody.getTransactionId());

            assertAll(
                    () -> assertEquals(ctx.tournamentEvent.getPayload().uuid(), transaction.getUuid(), "db.transaction.uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), transaction.getPlayerUuid(), "db.transaction.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.transaction.date"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().type(), transaction.getType(), "db.transaction.type"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().operation(), transaction.getOperation(), "db.transaction.operation"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().gameUuid(), transaction.getGameUuid(), "db.transaction.game_uuid"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().gameSessionUuid(), transaction.getGameSessionUuid(), "db.transaction.game_session_uuid"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().currency(), transaction.getCurrency(), "db.transaction.currency"),
                    () -> assertEquals(0, ctx.tournamentEvent.getPayload().amount().compareTo(transaction.getAmount()), "db.transaction.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.transaction.created_at"),
                    () -> assertEquals(ctx.tournamentEvent.getSequence(), transaction.getSeqnumber(), "db.transaction.seq_number"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().providerRoundClosed(), transaction.getProviderRoundClosed(), "db.transaction.provider_round_closed")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    ctx.registeredPlayer.walletData().playerUUID());

            assertAll(
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, BigDecimal.ZERO.add(tournamentAmount).compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька после турнирного выигрыша", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.tournamentEvent.getSequence())
                    .fetch();
            assertAll(
                    () -> assertEquals(ctx.tournamentEvent.getSequence(), aggregate.lastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(aggregate.availableWithdrawalBalance()), "redis.aggregate.available_withdrawal_balance"),
                    () -> assertTrue(aggregate.gambling().containsKey(ctx.tournamentEvent.getPayload().uuid()), "redis.aggregate.gambling.contains"),
                    () -> assertEquals(0, tournamentAmount.compareTo(aggregate.gambling().get(ctx.tournamentEvent.getPayload().uuid()).getAmount()), "redis.aggregate.gambling.amount"),
                    () -> assertNotNull(aggregate.gambling().get(ctx.tournamentEvent.getPayload().uuid()).getCreatedAt(), "redis.aggregate.gambling.created_at")
            );
        });

        step("Kafka: Проверка поступления сообщения турнира в топик wallet.projectionSource", () -> {
            var message = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.tournamentEvent.getSequence())
                    .fetch();

            assertTrue(utils.areEquivalent(message, ctx.tournamentEvent), "kafka.payload");
        });
    }
}
