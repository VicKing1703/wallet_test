package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий лимит хранения выигрышей в Redis.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что агрегат кошелька в Redis хранит не более {@code max-gambling-count} записей и корректно
 * обновляется даже при выполнении серии из {@code max + 1} операций выигрыша.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Контроль объема данных:</b>
 *     <p><b>Что проверяем:</b> Размер коллекции {@code Gambling} равен лимиту конфигурации.</p>
 *     <p><b>Почему это важно:</b> Избыточные данные в Redis увеличивают задержки и стоимость хранения.</p>
 *   </li>
 *   <li><b>Согласованность балансов:</b>
 *     <p><b>Что проверяем:</b> Баланс и {@code lastSeqNumber} соответствуют последнему событию {@code won_from_gamble}.</p>
 *     <p><b>Почему это важно:</b> Любое расхождение приведет к неверным отображениям в клиентах.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Создать игрока с нулевым балансом и игровую сессию.</li>
 *   <li>Выполнить {@code max + 1} операции выигрыша указанного типа.</li>
 *   <li>Дождаться события {@code won_from_gamble} для последнего выигрыша.</li>
 *   <li>Получить агрегат кошелька из Redis и убедиться в соблюдении лимита.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Каждый запрос {@code /win} завершается успешно.</li>
 *   <li>Redis хранит ровно {@code maxGamblingCountInRedis} записей и верный баланс.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/win")
@Suite("Позитивные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
@DisplayName("Проверка лимита агрегата Gambling транзакций в Redis для различных типов операций")
class WinGamblingHistoryLimitTest extends BaseParameterizedTest {

    private static final BigDecimal OPERATION_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal INITIAL_BALANCE = BigDecimal.ZERO;

    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> winOperationProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.WIN),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT)
        );
    }

    @ParameterizedTest(name = "операция выигрыша = {0}")
    @MethodSource("winOperationProvider")
    void testWinGamblingHistoryCountLimitInRedis(NatsGamblingTransactionOperation operationParam) {
        final int maxGamblingCountInRedis = 50;

        final int operationsToMake = maxGamblingCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            String lastTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastWinEvent;
            BigDecimal currentBalance;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_BALANCE);
            ctx.currentBalance = ctx.registeredPlayer.walletData().balance();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step(String.format("Manager API: Совершение %d операций типа %s", operationsToMake, operationParam), () -> {
            for (int i = 0; i < operationsToMake; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == operationsToMake - 1) {
                    ctx.lastTransactionId = transactionId;
                }

                WinRequestBody winRequestBody = WinRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                        .amount(OPERATION_AMOUNT)
                        .transactionId(transactionId)
                        .type(operationParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(true)
                        .build();

                var currentOperationNumber = i + 1;
                var currentTxId = transactionId;

                step(String.format("Совершение операции %s #%d с ID: %s", operationParam, currentOperationNumber, currentTxId), () -> {
                    var response = managerClient.win(
                            casinoId,
                            utils.createSignature(ApiEndpoints.WIN, winRequestBody),
                            winRequestBody);

                    ctx.currentBalance = ctx.currentBalance.add(OPERATION_AMOUNT);

                    assertAll(String.format("Проверка ответа API для операции %s #%d", operationParam, currentOperationNumber),
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertEquals(currentTxId, response.getBody().transactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, ctx.currentBalance.compareTo(response.getBody().balance()), "manager_api.body.balance")
                    );
                });
            }
        });

        step(String.format("NATS: Ожидание NATS-события won_from_gamble для последней операции %s (ID: %s)", operationParam, ctx.lastTransactionId), () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.lastWinEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.WON_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.lastTransactionId)
                    .fetch();

            assertNotNull(ctx.lastWinEvent, "nats.won_from_gamble");
        });

        step(String.format("Redis(Wallet): Получение и проверка данных кошелька для операции %s", operationParam), () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.lastWinEvent.getSequence())
                    .fetch();
            var gamblingTransactionsInRedis = aggregate.gambling();

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(maxGamblingCountInRedis, gamblingTransactionsInRedis.size(), "redis.wallet.gambling.count"),
                    () -> assertEquals(0, ctx.currentBalance.compareTo(aggregate.balance()), "redis.wallet.balance"),
                    () -> assertEquals((int) ctx.lastWinEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number")
            );
        });
    }
}
