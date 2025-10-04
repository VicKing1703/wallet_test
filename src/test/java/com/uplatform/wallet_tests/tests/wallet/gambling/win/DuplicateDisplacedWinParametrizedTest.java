package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный параметризованный тест, проверяющий API ответ при попытке совершить дублирующий выигрыш
 * на транзакцию, которая была вытеснена из кэша Redis. Тест покрывает различные типы операций
 * (WIN, FREESPIN, JACKPOT) и суммы (включая нулевую).
 * Ожидается, что система найдет транзакцию в основном хранилище и вернет идемпотентный ответ.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что API Manager корректно обрабатывает запрос на дублирующий выигрыш для каждого типа операции и суммы,
 * даже если информация об оригинальной транзакции отсутствует в "горячем" кеше Redis.
 * Тест ожидает, что система найдет транзакцию в основном хранилище и вернет успешный ответ {@link HttpStatus#OK}
 * с телом {@link GamblingResponseBody}, содержащим тот же {@code transactionId} и нулевой баланс.</p>
 *
 * <p><b>Сценарий теста (для каждой комбинации типа операции и суммы):</b></p>
 * <ol>
 *   <li><b>Регистрация игрока и создание сессии:</b> Подготавливается игрок и игровая сессия.</li>
 *   <li><b>Совершение базовой ставки:</b> Делается ставка, к которой будут привязаны выигрыши.</li>
 *   <li><b>Совершение вытесняющих выигрышей:</b> Через API совершается {@code maxGamblingCountInRedis + 1}
 *       запросов на выигрыш, чтобы гарантированно вытеснить одну транзакцию из кеша Redis.</li>
 *   <li><b>Получение Sequence последнего выигрыша:</b> Через NATS ожидается событие от последнего выигрыша
 *       для получения его {@code sequence number}.</li>
 *   <li><b>Определение вытесненной транзакции:</b> Запрашиваются данные из Redis для агрегата кошелька.
 *       Сравнивая список всех сделанных транзакций со списком в Redis, определяется ID транзакции,
 *       которая была вытеснена.</li>
 *   <li><b>Попытка дублирования вытесненной транзакции:</b> Через API отправляется новый запрос на выигрыш,
 *       используя тот же {@code transactionId}, что и у вытесненной транзакции.</li>
 *   <li><b>Проверка ответа API:</b> Ожидается, что API вернет успешный ответ со статусом {@link HttpStatus#OK}.
 *       Тело ответа ({@link GamblingResponseBody}) должно содержать {@code transactionId} из первого запроса и баланс,
 *       равный {@link BigDecimal#ZERO}.</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Негативные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class DuplicateDisplacedWinParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("100.00");
    private static final BigDecimal defaultWinAmount = new BigDecimal("1.00");

    static Stream<Arguments> winOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.WIN, defaultWinAmount),
                Arguments.of(NatsGamblingTransactionOperation.WIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, defaultWinAmount),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, defaultWinAmount),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, BigDecimal.ZERO)
        );
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("winOperationAndAmountProvider")
    @DisplayName("Дублирование выигрыша, вытесненного из кеша")
    void testDuplicateDisplacedWinReturnsIdempotentResponse(NatsGamblingTransactionOperation operationParam, BigDecimal winAmountParam)  {
        final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
        final int maxGamblingCountInRedis = 50;

        final int winsToMakeToDisplace = maxGamblingCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<WinRequestBody> allMadeWinRequests = new ArrayList<>();
            String lastMadeWinTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastWinNatsEvent;
            WinRequestBody displacedWinRequestToDuplicate;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Совершение выигрышей для вытеснения (тип: " + operationParam + ", сумма: " + winAmountParam + ")", () -> {
            for (int i = 0; i < winsToMakeToDisplace; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == winsToMakeToDisplace - 1) {
                    ctx.lastMadeWinTransactionId = transactionId;
                }
                var winRequestBody = WinRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                        .amount(winAmountParam)
                        .transactionId(transactionId)
                        .type(operationParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(i == winsToMakeToDisplace - 1)
                        .build();
                ctx.allMadeWinRequests.add(winRequestBody);

                var currentWinNumber = i + 1;
                step("Совершение выигрыша #" + currentWinNumber + " (ID: " + transactionId + ")", () -> {
                    var response = managerClient.win(
                            casinoId,
                            utils.createSignature(ApiEndpoints.WIN, winRequestBody),
                            winRequestBody);
                    assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code");
                });
            }
        });

        step("NATS: Ожидание NATS-события для последнего выигрыша", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.lastWinNatsEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.WON_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.lastMadeWinTransactionId)
                    .fetch();

            assertNotNull(ctx.lastWinNatsEvent, "nats.win_event");
        });

        step("Redis: Определение вытесненной транзакции", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.lastWinNatsEvent.getSequence())
                    .fetch();

            var transactionIdsCurrentlyInRedis = aggregate.gambling().keySet();

            var allMadeTransactionIds = ctx.allMadeWinRequests.stream()
                    .map(WinRequestBody::getTransactionId).collect(Collectors.toCollection(HashSet::new));
            allMadeTransactionIds.removeAll(transactionIdsCurrentlyInRedis);
            assertEquals(1, allMadeTransactionIds.size(), "redis.displaced_transaction.count");
            var displacedTxId = allMadeTransactionIds.iterator().next();

            ctx.displacedWinRequestToDuplicate = ctx.allMadeWinRequests.stream()
                    .filter(winReq -> winReq.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));
        });

        step("Manager API: Попытка дублирования вытесненной транзакции", () -> {
            var duplicateResponse = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.displacedWinRequestToDuplicate),
                    ctx.displacedWinRequestToDuplicate
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("Проверка ответа на дубликат вытесненной транзакции",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.status_code"),
                    () -> assertNotNull(responseBody, "manager_api.response_body"),
                    () -> assertEquals(ctx.displacedWinRequestToDuplicate.getTransactionId(), responseBody.transactionId(), "manager_api.transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.balance()), "manager_api.balance")
            );
        });
    }
}
