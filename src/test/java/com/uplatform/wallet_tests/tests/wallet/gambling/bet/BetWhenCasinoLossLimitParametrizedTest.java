package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
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
 * Интеграционный тест, проверяющий механизм ограничения потерь в казино (Casino Loss Limit) при совершении ставок.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить абсолютную надежность и точность работы функционала "Ответственная игра" (Responsible Gaming)
 * в части контроля лимитов потерь. Система должна действовать как жесткий барьер, автоматически и мгновенно отклоняя любую
 * транзакцию на списание, если ее выполнение приведет к превышению лимита потерь, установленного игроком. Это гарантирует,
 * что финансовые потери игрока за определенный период никогда не превысят заданного им значения.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Контроль лимита в реальном времени:</b>
 *     <p><b>Что проверяем:</b> Попытки совершить ставку ({@code POST /bet}), сумма которой превышает остаток доступного лимита потерь.
 *     <p><b>Почему это важно:</b> Проверка должна происходить синхронно с запросом на ставку, <b>до</b> списания средств.
 *     Любая задержка или ошибка в расчетах может привести к нарушению установленного лимита, что недопустимо с точки зрения
 *     требований регуляторов и доверия игрока.
 *   </li>
 *   <li><b>Универсальность ограничения:</b>
 *     <p><b>Что проверяем:</b> Применение лимита ко всем типам расходных операций в казино ({@code BET}, {@code TIPS}, {@code FREESPIN}).
 *     <p><b>Почему это важно:</b> Лимит должен учитывать все способы списания средств в рамках игровой активности.
 *     Наличие "лазеек" через определенные типы транзакций сделало бы механизм ограничения бесполезным.
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Игроку устанавливается дневной лимит на проигрыш через Public API.</li>
 *   <li>Игрок (имея достаточный баланс) пытается совершить ставку на сумму, превышающую установленный лимит.</li>
 *   <li>Проверяется, что система отклоняет эту операцию.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает ошибку с кодом {@link GamblingErrors#LIMIT_IS_OVER}.</li>
 *   <li>Баланс игрока остается неизменным, списание не происходит.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class BetWhenCasinoLossLimitParametrizedTest extends BaseNegativeParameterizedTest {

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

        step("Public API: Установка лимита на проигрыш", () -> {
            var request = SetCasinoLossLimitRequest.builder()
                    .currency(registeredPlayer.walletData().currency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(LIMIT_AMOUNT.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
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
    @DisplayName("Совершение ставки в казино, превышающей CasinoLossLimit:")
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
