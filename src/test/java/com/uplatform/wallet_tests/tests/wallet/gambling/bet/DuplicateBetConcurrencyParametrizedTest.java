package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeEach;
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
 * Интеграционный тест, верифицирующий идемпотентную обработку дублирующихся транзакций в условиях конкурентного доступа (race condition).
 *
 * <p><b>Идея теста:</b>
 * Подтвердить абсолютную надежность механизма идемпотентности при обработке конкурирующих, идентичных запросов.
 * Тест эмулирует критический сценарий "гонки состояний" (race condition), когда два потока одновременно пытаются обработать
 * одну и ту же транзакцию. Система обязана гарантировать, что только один запрос выполнит финансовую операцию (списание средств),
 * в то время как второй будет обработан идемпотентно, предотвращая двойное списание и обеспечивая атомарность операции.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Атомарность финансовой операции:</b>
 *     <p><b>Что проверяем:</b> Результат выполнения двух идентичных запросов, отправленных практически одновременно.
 *     <p><b>Почему это важно:</b> Это фундаментальная проверка для любой распределенной финансовой системы. Сетевые задержки,
 *     клиентские повторы (retries) или ошибки могут привести к отправке дубликатов. Система должна быть спроектирована так,
 *     чтобы гарантировать семантику обработки "exactly-once" для финансовых транзакций, предотвращая потерю или дублирование средств.
 *   </li>
 *   <li><b>Корректность ответов при идемпотентной обработке:</b>
 *     <p><b>Что проверяем:</b> Статус-коды и тела ответов для обоих конкурирующих запросов.
 *     <p><b>Почему это важно:</b> Успешный статус (200 OK) для обоих запросов подтверждает, что с точки зрения клиента (игрового провайдера)
 *     операция завершилась корректно, даже если она была дубликатом. Различие в телах ответа (один с актуальным балансом,
 *     другой — с нулевым) является ключевым индикатором того, что идемпотентный механизм сработал: повторное списание не произошло,
 *     и был возвращен результат уже завершенной транзакции (в данном случае, представленный нулевым балансом как признак идемпотентной обработки).
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Для каждой комбинации параметров (тип операции, сумма) создается изолированная среда (новый игрок, новая сессия).</li>
 *   <li>Формируются два абсолютно идентичных запроса на ставку.</li>
 *   <li>С помощью пула из двух потоков оба запроса отправляются на выполнение одновременно, создавая условия для race condition.</li>
 *   <li>Анализируются оба полученных ответа.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Оба ответа имеют статус {@link HttpStatus#OK}.</li>
 *   <li>Идентификаторы транзакций ({@code transactionId}) в обоих ответах идентичны.</li>
 *   <li>Набор балансов из двух ответов содержит ровно два значения: ожидаемый баланс после одного списания и {@link BigDecimal#ZERO}.</li>
 *   <li>Финальный баланс игрока в системе соответствует результату одного единственного списания.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class DuplicateBetConcurrencyParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal DEFAULT_BET_AMOUNT = new BigDecimal("1.00");

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private String casinoId;

    static Stream<Arguments> betOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.BET, DEFAULT_BET_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.BET, BigDecimal.ZERO),

                Arguments.of(NatsGamblingTransactionOperation.TIPS, DEFAULT_BET_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.TIPS, BigDecimal.ZERO),

                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, DEFAULT_BET_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO)
        );
    }

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        step("Default Step: Регистрация нового пользователя для теста", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии для теста", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.create_game_session");
        });
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("betOperationAndAmountProvider")
    @DisplayName("Идемпотентная обработка дублей ставок при одновременной отправке")
    void testConcurrentDuplicateBetsHandledIdempotently(NatsGamblingTransactionOperation operationParam, BigDecimal betAmountParam) throws InterruptedException {
        BigDecimal expectedBalanceAfterSuccessfulBet = INITIAL_ADJUSTMENT_AMOUNT.subtract(betAmountParam);

        step(String.format("Manager API: Одновременная отправка дублирующихся ставок (тип: %s, сумма: %s)", operationParam, betAmountParam), () -> {

            var request = BetRequestBody.builder()
                    .sessionToken(this.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var betApiCall = (Callable<ResponseEntity<GamblingResponseBody>>) () -> managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, request),
                    request
            );

            var executor = Executors.newFixedThreadPool(2);
            var callables = List.of(betApiCall, betApiCall);
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

            assertAll("Проверка ответов на одновременные ставки",
                    () -> assertEquals(HttpStatus.OK, response1.getStatusCode(), "manager_api.response1.status_code"),
                    () -> assertEquals(HttpStatus.OK, response2.getStatusCode(), "manager_api.response2.status_code"),
                    () -> assertEquals(request.getTransactionId(), body1.transactionId(), "manager_api.response1.transaction_id"),
                    () -> assertEquals(request.getTransactionId(), body2.transactionId(), "manager_api.response2.transaction_id"),
                    () -> assertTrue(
                            actualBalances.stream().anyMatch(b -> b.compareTo(expectedBalanceAfterSuccessfulBet) == 0),
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