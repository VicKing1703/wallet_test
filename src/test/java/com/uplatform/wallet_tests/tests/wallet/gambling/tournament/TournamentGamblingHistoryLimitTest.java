package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет ограничение по количеству турнирных транзакций, хранимых в агрегате кошелька Redis.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что после выполнения более {@value #MAX_GAMBLING_COUNT_IN_REDIS} выигрышей в кеше остается
 * только максимально допустимое число записей, а вытеснение происходит корректно.
 * Это подтверждает соблюдение конфигурационного лимита и предотвращает разрастание агрегата.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Стабильность API при массовых начислениях:</b>
 *     <p><b>Что проверяем:</b> Успешную обработку серии запросов {@code POST /tournament}.</p>
 *     <p><b>Почему это важно:</b> Сервис должен корректно выдерживать нагрузки и сохранять баланс игрока.</p>
 *   </li>
 *   <li><b>Лимит на истории в Redis:</b>
 *     <p><b>Что проверяем:</b> Размер коллекции {@code gambling} в агрегате после серии операций.</p>
 *     <p><b>Почему это важно:</b> Соблюдение лимита гарантирует предсказуемую производительность Redis.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Зарегистрировать игрока и создать игровую сессию.</li>
 *   <li>Совершить {@value #OPERATIONS_TO_MAKE} последовательных турнирных выигрышей.</li>
 *   <li>Дождаться события {@code tournament_won_from_gamble} для последней операции.</li>
 *   <li>Запросить агрегат кошелька в Redis и проверить размер истории и баланс.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Каждое начисление завершается со статусом {@link HttpStatus#OK} и корректным балансом.</li>
 *   <li>Коллекция {@code gambling} содержит ровно {@value #MAX_GAMBLING_COUNT_IN_REDIS} записей.</li>
 *   <li>Последовательность и баланс в агрегате совпадают с данными последнего события.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Позитивные сценарии: /tournament")
@Tag("Gambling")
@Tag("Wallet")
@DisplayName("Проверка лимита агрегата Gambling транзакций в Redis для турнирных выигрышей")
class TournamentGamblingHistoryLimitTest extends BaseTest {

    private static final BigDecimal OPERATION_AMOUNT = new BigDecimal("5.00");
    private static final BigDecimal INITIAL_BALANCE = BigDecimal.ZERO;
    private static final int MAX_GAMBLING_COUNT_IN_REDIS = 50;
    private static final int OPERATIONS_TO_MAKE = MAX_GAMBLING_COUNT_IN_REDIS + 1;

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    @Test
    @DisplayName("Проверка вытеснения турнирных выигрышей из кэша Redis")
    void testTournamentHistoryCountLimitInRedis() {

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            String lastTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastTournamentEvent;
            BigDecimal currentBalance;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя с нулевым балансом", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_BALANCE);
            ctx.currentBalance = ctx.registeredPlayer.walletData().balance();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step(String.format("Manager API: Совершение %d турнирных выигрышей", OPERATIONS_TO_MAKE), () -> {
            for (int i = 0; i < OPERATIONS_TO_MAKE; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == OPERATIONS_TO_MAKE - 1) {
                    ctx.lastTransactionId = transactionId;
                }

                var tournamentRequestBody = TournamentRequestBody.builder()
                        .amount(OPERATION_AMOUNT)
                        .playerId(ctx.registeredPlayer.walletData().walletUUID())
                        .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                        .transactionId(transactionId)
                        .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                        .roundId(UUID.randomUUID().toString())
                        .providerUuid(ctx.gameLaunchData.dbGameSession().getProviderUuid())
                        .build();

                var currentOperationNumber = i + 1;
                var currentTxId = transactionId;

                step(String.format("Совершение турнирного выигрыша #%d с ID: %s", currentOperationNumber, currentTxId), () -> {
                    var response = managerClient.tournament(
                            casinoId,
                            utils.createSignature(ApiEndpoints.TOURNAMENT, tournamentRequestBody),
                            tournamentRequestBody);

                    ctx.currentBalance = ctx.currentBalance.add(OPERATION_AMOUNT);

                    assertAll(String.format("Проверка ответа API для турнирного выигрыша #%d", currentOperationNumber),
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertNotNull(response.getBody(), "manager_api.body_not_null"),
                            () -> assertEquals(currentTxId, response.getBody().transactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, ctx.currentBalance.compareTo(response.getBody().balance()), "manager_api.body.balance")
                    );
                });
            }
        });

        step(String.format("NATS: Ожидание NATS-события tournament_won_from_gamble для последней операции (ID: %s)", ctx.lastTransactionId), () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.lastTournamentEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.lastTransactionId)
                    .fetch();

            assertNotNull(ctx.lastTournamentEvent, "nats.tournament_won_from_gamble");
            assertEquals(NatsGamblingTransactionOperation.TOURNAMENT, ctx.lastTournamentEvent.getPayload().operation(), "nats.payload.operation_type");
        });

        step("Redis(Wallet): Получение и проверка данных кошелька после серии турнирных выигрышей", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.lastTournamentEvent.getSequence())
                    .fetch();
            var gamblingTransactionsInRedis = aggregate.gambling();

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(MAX_GAMBLING_COUNT_IN_REDIS, gamblingTransactionsInRedis.size(), "redis.wallet.gambling.count"),
                    () -> assertEquals(0, ctx.currentBalance.compareTo(aggregate.balance()), "redis.wallet.balance"),
                    () -> assertEquals((int) ctx.lastTournamentEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number")
            );
        });
    }
}
