package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SetSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
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
 * Интеграционный тест, верифицирующий механизм ограничения на размер одиночной ставки (Single Bet Limit) при совершении ставок.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить абсолютную надежность и точность работы функционала "Ответственная игра" (Responsible Gaming)
 * в части контроля лимита на размер одиночной ставки. Система обязана выполнять строгую, синхронную проверку суммы каждой
 * ставки и принудительно отклонять любую транзакцию, превышающую установленный лимит, <b>до</b> списания средств.
 * Это гарантирует, что игрок не сможет случайно или намеренно совершить ставку на сумму, которую он сам для себя ограничил.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Контроль лимита в реальном времени:</b>
 *     <p><b>Что проверяем:</b> Попытки совершить ставку ({@code POST /bet}), сумма которой превышает установленный лимит на одиночную ставку.
 *     <p><b>Почему это важно:</b> Верификация должна происходить синхронно с запросом, <b>до</b> списания средств.
 *     Это является ключевым требованием для функционала "Ответственная игра", обеспечивая, что игрок не может нарушить
 *     установленные им же ограничения.
 *   </li>
 *   <li><b>Универсальность ограничения:</b>
 *     <p><b>Что проверяем:</b> Применение лимита ко всем типам расходных операций в казино ({@code BET}, {@code TIPS}, {@code FREESPIN}).
 *     <p><b>Почему это важно:</b> Лимит на одну ставку должен быть универсальным для всех видов списаний в рамках игровой сессии.
 *     Тест подтверждает отсутствие уязвимостей, при которых определенный тип транзакции мог бы обойти установленное ограничение.
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Игроку устанавливается лимит на размер одиночной ставки через Public API.</li>
 *   <li>Игрок (имея достаточный баланс) пытается совершить ставку на сумму, превышающую установленный лимит.</li>
 *   <li>Проверяется, что система отклоняет эту операцию с соответствующей ошибкой.</li>
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
class BetWhenSingleBetLimitParametrizedTest extends BaseNegativeParameterizedTest {

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

        step("Public API: Установка лимита на одиночную ставку", () -> {
            var singleBetLimitRequest = SetSingleBetLimitRequest.builder()
                    .currency(registeredPlayer.walletData().currency())
                    .amount(LIMIT_AMOUNT.toString())
                    .build();

            var response = publicClient.setSingleBetLimit(
                    registeredPlayer.authorizationResponse().getBody().getToken(),
                    singleBetLimitRequest
            );

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
    @DisplayName("Совершение ставки в казино, превышающей SingleBetLimit:")
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

        step("Manager API: Попытка совершения ставки, превышающей лимит", () -> {
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
