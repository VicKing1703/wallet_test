package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
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
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий идемпотентность выигрыша при параллельных запросах.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить, что при одновременном повторе одного и того же выигрыша система начисляет средства только
 * один раз, а дубликат получает нулевой баланс.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Идемпотентность под нагрузкой:</b>
 *     <p><b>Что проверяем:</b> Оба ответа имеют {@link HttpStatus#OK}, но только один содержит новый баланс.</p>
 *     <p><b>Почему это важно:</b> Параллельные ретраи часто встречаются в проде; двойные начисления недопустимы.</p>
 *   </li>
 *   <li><b>Согласованность идентификаторов:</b>
 *     <p><b>Что проверяем:</b> Оба ответа используют один {@code transactionId}.</p>
 *     <p><b>Почему это важно:</b> Это ключевой механизм идемпотентности и трекинга транзакций.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Создать игрока и игровую сессию.</li>
 *   <li>Совершить базовую ставку и зафиксировать баланс.</li>
 *   <li>Отправить два идентичных запроса {@code /win} в параллельных потоках.</li>
 *   <li>Сравнить ответы и убедиться, что начисление произошло только один раз.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Оба ответа возвращаются со статусом {@link HttpStatus#OK}.</li>
 *   <li>Один ответ содержит новый баланс, второй — {@link BigDecimal#ZERO}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Негативные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class DuplicateWinConcurrencyParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal BASE_BET_AMOUNT = new BigDecimal("10.00");
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
    @DisplayName("Идемпотентная обработка дублей выигрышей при одновременной отправке")
    void testConcurrentDuplicateWinsHandledIdempotently(NatsGamblingTransactionOperation operationParam, BigDecimal winAmountParam) throws InterruptedException {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody initialBetRequest;
            BigDecimal balanceAfterBet;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя для теста", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии для теста", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Default Step: Совершение базовой ставки для привязки выигрыша", () -> {
            ctx.initialBetRequest = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BASE_BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.initialBetRequest),
                    ctx.initialBetRequest);

            assertAll("default_step.base_bet_response",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "default_step.base_bet_status_code"),
                    () -> assertNotNull(response.getBody(), "default_step.base_bet_response_body_not_null")
            );
            ctx.balanceAfterBet = response.getBody().balance();
        });

        BigDecimal expectedBalanceAfterSuccessfulWin = ctx.balanceAfterBet.add(winAmountParam);

        step(String.format("Manager API: Одновременная отправка дублирующихся выигрышей (тип: %s, сумма: %s)", operationParam, winAmountParam), () -> {

            var request = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(ctx.initialBetRequest.getRoundId())
                    .roundClosed(false)
                    .build();

            Callable<ResponseEntity<GamblingResponseBody>> winApiCall = () -> managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, request),
                    request
            );

            var executor = Executors.newFixedThreadPool(2);
            var callables = List.of(winApiCall, winApiCall);
            var futures = executor.invokeAll(callables);
            executor.shutdown();

            var results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            fail("Один из параллельных запросов завершился с исключением", e);
                            return null;
                        }
                    }).collect(Collectors.toList());

            var response1 = results.get(0);
            var response2 = results.get(1);
            var body1 = response1.getBody();
            var body2 = response2.getBody();

            assertNotNull(body1, "manager_api.response1.body_is_null");
            assertNotNull(body2, "manager_api.response2.body_is_null");

            Set<BigDecimal> actualBalances = Set.of(body1.balance(), body2.balance());

            assertAll("Проверка ответов на одновременные выигрыши",
                    () -> assertEquals(HttpStatus.OK, response1.getStatusCode(), "manager_api.response1.status_code"),
                    () -> assertEquals(HttpStatus.OK, response2.getStatusCode(), "manager_api.response2.status_code"),
                    () -> assertEquals(request.getTransactionId(), body1.transactionId(), "manager_api.response1.transaction_id"),
                    () -> assertEquals(request.getTransactionId(), body2.transactionId(), "manager_api.response2.transaction_id"),
                    () -> assertTrue(
                            actualBalances.stream().anyMatch(b -> b.compareTo(expectedBalanceAfterSuccessfulWin) == 0),
                            "manager_api.responses.balances.no_expected_balance"
                    ),
                    () -> assertTrue(
                            actualBalances.stream().anyMatch(b -> b.compareTo(BigDecimal.ZERO) == 0),
                            "manager_api.responses.balances.no_zero_balance"
                    )
            );
        });
    }
}
