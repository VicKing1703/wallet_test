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
import org.junit.jupiter.api.BeforeAll;
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
 * Интеграционный тест, проверяющий идемпотентность выигрыша, вытесненного из кеша Redis.
 *
 * <p><b>Идея теста:</b>
 * Смоделировать ситуацию, когда оригинальная транзакция выигрыша исчезла из Redis, но должна быть найдена в БД.
 * Повторный запрос обязан завершиться успешно со статусом {@link HttpStatus#OK} и нулевым балансом.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Работа лимита кеша:</b>
 *     <p><b>Что проверяем:</b> При превышении {@code max-gambling-count} старые транзакции удаляются.</p>
 *     <p><b>Почему это важно:</b> Очистка кеша не должна ломать повторные клиентские запросы.</p>
 *   </li>
 *   <li><b>Идемпотентность API:</b>
 *     <p><b>Что проверяем:</b> Повторный {@code transactionId} возвращает тело {@link GamblingResponseBody}
 *     с балансом {@link BigDecimal#ZERO}.</p>
 *     <p><b>Почему это важно:</b> Гарантирует отсутствие двойного начисления выигрыша.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Создать игрока и игровую сессию.</li>
 *   <li>Совершить {@code max + 1} выигрышей, чтобы вытеснить раннюю транзакцию из Redis.</li>
 *   <li>Найти вытесненный {@code transactionId} по разнице между полным списком и данными Redis.</li>
 *   <li>Отправить дубликат выигрыша и проверить ответ.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Каждый исходный запрос завершаетcя с {@link HttpStatus#OK}.</li>
 *   <li>Дубликат возвращает тот же {@code transactionId} и нулевой баланс.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Негативные сценарии: /win")
@Tag("Gambling") @Tag("Wallet7")
class DuplicateDisplacedWinParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal DEFAULT_WIN_AMOUNT = new BigDecimal("1.00");

    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> winOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.WIN, DEFAULT_WIN_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.WIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, DEFAULT_WIN_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, DEFAULT_WIN_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, BigDecimal.ZERO)
        );
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("winOperationAndAmountProvider")
    @DisplayName("Дублирование выигрыша, вытесненного из кеша")
    void testDuplicateDisplacedWinReturnsIdempotentResponse(
            NatsGamblingTransactionOperation operationParam,
            BigDecimal winAmountParam
    ) {
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
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
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
