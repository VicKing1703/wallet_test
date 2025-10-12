package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
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
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, который проверяет механизм ротации и ограничения истории гэмблинг-транзакций в кэше Redis.
 *
 * <p><b>Идея теста:</b> Гарантировать стабильность и производительность системы при высокой интенсивности игровых
 * операций. Бесконечное накопление истории транзакций в кэше Redis может привести к исчерпанию памяти и замедлению
 * отклика. Этот тест подтверждает, что в системе реализован надежный механизм ротации (FIFO - First-In, First-Out),
 * который автоматически удаляет самые старые записи при достижении лимита, сохраняя в памяти только N последних
 * транзакций. Это обеспечивает предсказуемое потребление ресурсов и высокую производительность даже под большой нагрузкой.</p>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <p>Тест эмулирует сценарий интенсивной игровой активности, при котором игрок совершает количество транзакций,
 * превышающее установленный лимит хранения в Redis (N+1). Проверяется, что после выполнения всех операций в кэше
 * останется ровно N самых последних транзакций, а самая первая будет вытеснена. Сценарий повторяется для всех
 * ключевых типов игровых операций (BET, TIPS, FREESPIN).</p>
 *
 * <p><b>Последовательность действий для каждого типа операции:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока с балансом, достаточным для совершения N+1 операций.</li>
 *   <li>Создание игровой сессии.</li>
 *   <li>В цикле выполняется N+1 ({@code maxGamblingCountInRedis + 1}) вызов API {@code /bet} с указанным типом операции.</li>
 *   <li>После всех вызовов, тест ожидает NATS-событие от <b>последней</b> транзакции, чтобы получить ее {@code sequence number}
 *       для обеспечения консистентности данных при чтении из Redis.</li>
 *   <li>Выполняется запрос к Redis за актуальным состоянием кошелька.</li>
 *   <li>Проверяется, что в агрегате кошелька в Redis хранится ровно N ({@code maxGamblingCountInRedis}) записей о транзакциях.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Количество записей в истории гэмблинг-транзакций в Redis точно равно установленному лимиту ({@code maxGamblingCountInRedis}).</li>
 *   <li>Итоговый баланс игрока в Redis корректно отражает списание средств за все N+1 операции.</li>
 *   <li>Поле {@code lastSeqNumber} в Redis соответствует номеру последовательности последней совершенной операции.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Позитивные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
@DisplayName("Проверка лимита агрегата Gambling транзакций в Redis для различных типов операций")
class BetGamblingHistoryLimitTest extends BaseParameterizedTest {

    private static final BigDecimal operationAmount = new BigDecimal("1.00");

    static Stream<Arguments> gamblingOperationProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.BET),
                Arguments.of(NatsGamblingTransactionOperation.TIPS),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN)
        );
    }

    @ParameterizedTest(name = "операция = {0}")
    @MethodSource("gamblingOperationProvider")
    void testGamblingHistoryCountLimitInRedis(NatsGamblingTransactionOperation operationParam) {
        final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
        final int maxGamblingCountInRedis = 50;

        final int operationsToMake = maxGamblingCountInRedis + 1;
        final BigDecimal dynamicInitialAdjustmentAmount = operationAmount
                .multiply(new BigDecimal(operationsToMake))
                .add(new BigDecimal("10.00"));

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            String lastTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastBetEvent;
            BigDecimal currentBalance;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(dynamicInitialAdjustmentAmount);
            ctx.currentBalance = ctx.registeredPlayer.walletData().balance();

            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии для операции", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);

            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step(String.format("Manager API: Совершение %d операций типа %s", operationsToMake, operationParam), () -> {
            for (int i = 0; i < operationsToMake; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == operationsToMake - 1) {
                    ctx.lastTransactionId = transactionId;
                }

                var betRequestBody = BetRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                        .amount(operationAmount)
                        .transactionId(transactionId)
                        .type(operationParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(false)
                        .build();

                var currentOperationNumber = i + 1;
                var currentTxId = transactionId;

                step(String.format("Совершение операции %s #%d с ID: %s", operationParam, currentOperationNumber, currentTxId), () -> {
                    var response = managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, betRequestBody),
                            betRequestBody);

                    ctx.currentBalance = ctx.currentBalance.subtract(operationAmount);

                    assertAll(String.format("Проверка ответа API для операции %s #%d", operationParam, currentOperationNumber),
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertEquals(currentTxId, response.getBody().transactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, ctx.currentBalance.compareTo(response.getBody().balance()), "manager_api.body.balance")
                    );
                });
            }
        });

        step(String.format("NATS: Ожидание NATS-события betted_from_gamble для последней операции %s (ID: %s)", operationParam, ctx.lastTransactionId), () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.lastBetEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.lastTransactionId)
                    .fetch();

            assertNotNull(ctx.lastBetEvent, "nats.betted_from_gamble");
        });

        step(String.format("Redis(Wallet): Получение и проверка данных кошелька для операции %s", operationParam), () -> {
            step("Redis(Wallet): Получение и проверка данных кошелька (лимит iFrame ставок)", () -> {
                var aggregate = redisWalletClient
                        .key(ctx.registeredPlayer.walletData().walletUUID())
                        .withAtLeast("LastSeqNumber", (int) ctx.lastBetEvent.getSequence())
                        .fetch();

                var gamblingTransactionsInRedis = aggregate.gambling();

                assertAll("Проверка данных в Redis",
                        () -> assertEquals(maxGamblingCountInRedis, gamblingTransactionsInRedis.size(), "redis.wallet.gambling.count"),
                        () -> assertEquals(0, ctx.currentBalance.compareTo(aggregate.balance()), "redis.wallet.balance"),
                        () -> assertEquals((int) ctx.lastBetEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number")
                );
            });
        });
    }
}
