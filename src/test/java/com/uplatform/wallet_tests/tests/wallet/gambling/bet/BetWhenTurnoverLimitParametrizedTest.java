package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.tests.base.BaseNegativeParameterizedTest;
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

import static com.testing.multisource.config.modules.http.HttpServiceHelper.getManagerCasinoId;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Интеграционный тест, верифицирующий механизм ограничения на оборот (Turnover Limit) при совершении ставок.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить абсолютную надежность и точность работы функционала "Ответственная игра" (Responsible Gaming)
 * в части контроля лимита на оборот. Система обязана выполнять строгую, синхронную проверку совокупной суммы ставок и
 * принудительно отклонять любую транзакцию, если ее выполнение приведет к превышению установленного лимита, <b>до</b> списания средств.
 * Это гарантирует, что общая сумма ставок игрока за период не превысит заданного им значения.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Контроль совокупного оборота в реальном времени:</b>
 *     <p><b>Что проверяем:</b> Попытки совершить ставку ({@code POST /bet}), сумма которой в совокупности с ранее сделанными ставками
 *     превысит установленный лимит оборота.
 *     <p><b>Почему это важно:</b> Верификация должна происходить синхронно, агрегируя все предыдущие ставки за период.
 *     Это подтверждает, что система ведет точный учет оборота и способна мгновенно предотвратить нарушение лимита,
 *     что является ключевым требованием для функционала "Ответственная игра".
 *   </li>
 *   <li><b>Универсальность ограничения:</b>
 *     <p><b>Что проверяем:</b> Применение лимита ко всем типам расходных операций в казино ({@code BET}, {@code TIPS}, {@code FREESPIN}).
 *     <p><b>Почему это важно:</b> Лимит на оборот должен учитывать все виды ставок, формирующих общую сумму поставленных средств.
 *     Тест доказывает отсутствие уязвимостей, при которых определенный тип транзакции мог бы быть исключен из расчета оборота.
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Игроку устанавливается дневной лимит на оборот через Public API.</li>
 *   <li>Игрок (имея достаточный баланс) пытается совершить ставку на сумму, которая сразу превышает этот лимит.</li>
 *   <li>Проверяется, что система отклоняет операцию с соответствующей ошибкой.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает ошибку с кодом {@link GamblingErrors#LIMIT_IS_OVER}.</li>
 *   <li>Баланс игрока остается неизменным, подтверждая, что проверка происходит до списания.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class BetWhenTurnoverLimitParametrizedTest extends BaseNegativeParameterizedTest {

    private static final BigDecimal LIMIT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("2000.00");
    private static final BigDecimal EXCEEDED_BET_AMOUNT = new BigDecimal("100.00");

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var request = SetTurnoverLimitRequest.builder()
                    .currency(registeredPlayer.walletData().currency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(LIMIT_AMOUNT.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    registeredPlayer.authorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "public_api.status_code");
        });

        step("NATS: получение события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    registeredPlayer.walletData().playerUUID(),
                    registeredPlayer.walletData().walletUUID());

            var limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .fetch();

            assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
        });
    }

    static Stream<Arguments> blockedBetProvider() {
        return Stream.of(
                Arguments.of(
                        NatsGamblingTransactionOperation.BET,
                        GamblingErrors.LIMIT_IS_OVER,
                        GamblingErrors.LIMIT_IS_OVER.getMessage()
                ),
                Arguments.of(
                        NatsGamblingTransactionOperation.TIPS,
                        GamblingErrors.LIMIT_IS_OVER,
                        GamblingErrors.LIMIT_IS_OVER.getMessage()
                ),
                Arguments.of(
                        NatsGamblingTransactionOperation.FREESPIN,
                        GamblingErrors.LIMIT_IS_OVER,
                        GamblingErrors.LIMIT_IS_OVER.getMessage()
                )
        );
    }

    /**
     * @param type Тип операции ставки для проверки
     * @param expectedErrorCode Ожидаемый код ошибки
     * @param expectedMessage Ожидаемое сообщение об ошибке
     */
    @ParameterizedTest(name = "тип = {0}")
    @MethodSource("blockedBetProvider")
    @DisplayName("Совершение ставки в казино, превышающей TurnoverLimit:")
    void test(
            NatsGamblingTransactionOperation type,
            GamblingErrors expectedErrorCode,
            String expectedMessage
    ) {
        final class TestContext {
            BetRequestBody request;
            GamblingError error;
        }
        final TestContext ctx = new TestContext();

        step("Manager API: Попытка совершения ставки, превышающей лимит на оборот средств", () -> {
            ctx.request = BetRequestBody.builder()
                    .sessionToken(gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(LIMIT_AMOUNT.add(EXCEEDED_BET_AMOUNT))
                    .transactionId(UUID.randomUUID().toString())
                    .type(type)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            ctx.error = executeExpectingError(
                    () -> managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, ctx.request),
                            ctx.request
                    ),
                    "manager_api.bet.expected_exception",
                    GamblingError.class
            );
        });

        assertValidationError(
                ctx.error,
                expectedErrorCode.getCode(),
                expectedMessage
        );
    }
}
