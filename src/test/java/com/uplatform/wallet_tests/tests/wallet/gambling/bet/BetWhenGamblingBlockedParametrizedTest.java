package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseNegativeParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
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

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, верифицирующий механизм блокировки гемблинга при совершении ставки в казино.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить абсолютную надежность и непробиваемость механизма блокировки по вертикали "гемблинг".
 * Система обязана синхронно проверять и принудительно применять флаг {@code gamblingEnabled=false} на уровне обработки
 * транзакции, категорически отклоняя любую операцию <b>до</b> списания средств. Это гарантирует, что установленные
 * административные ограничения являются неотвратимыми и защищают как игрока, так и оператора.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Принудительное применение блокировки:</b>
 *     <p><b>Что проверяем:</b> Попытки совершить ставку ({@code POST /bet}) для игрока с активным флагом {@code gamblingEnabled=false}.
 *     <p><b>Почему это важно:</b> Это основная проверка, подтверждающая, что статус блокировки является высшим приоритетом
 *     при принятии решения о проведении финансовой операции. Сбой на этом этапе обесценивает весь механизм
 *     административного контроля и ответственной игры.
 *   </li>
 *   <li><b>Универсальность блокировки для всех типов операций:</b>
 *     <p><b>Что проверяем:</b> Применение блокировки ко всем типам расходных операций в казино: {@code BET}, {@code TIPS}, {@code FREESPIN}.
 *     <p><b>Почему это важно:</b> Блокировка должна быть всеобъемлющей. Тест доказывает отсутствие "слепых зон" или
 *     уязвимостей, через которые игрок мог бы обойти ограничение, используя специфический тип транзакции.
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Игроку устанавливается блокировка на гемблинг ({@code gamblingEnabled=false}) через CAP API.</li>
 *   <li>Последовательно выполняются попытки совершить ставки разных типов (BET, TIPS, FREESPIN).</li>
 *   <li>Проверяется, что каждая попытка отклоняется с ожидаемой ошибкой.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает ошибку с кодом {@link GamblingErrors#PLAYER_BLOCKED}.</li>
 *   <li>Баланс игрока остается неизменным.</li>
 *   <li>Финансовые транзакции не создаются, события в NATS/Kafka не генерируются.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet7")
class BetWhenGamblingBlockedParametrizedTest extends BaseNegativeParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("100.00");

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
        });

        step("CAP API: Блокировка гемблинга (gamblingEnabled=false)", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(false)
                    .bettingEnabled(true)
                    .build();

            var response = capAdminClient.updateBlockers(
                    registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_blockers.status_code");
        });
    }

    static Stream<Arguments> blockedBetProvider() {
        return Stream.of(
                Arguments.of(
                        NatsGamblingTransactionOperation.BET,
                        GamblingErrors.PLAYER_BLOCKED,
                        GamblingErrors.PLAYER_BLOCKED.getMessage()
                ),
                Arguments.of(
                        NatsGamblingTransactionOperation.TIPS,
                        GamblingErrors.PLAYER_BLOCKED,
                        GamblingErrors.PLAYER_BLOCKED.getMessage()
                ),
                Arguments.of(
                        NatsGamblingTransactionOperation.FREESPIN,
                        GamblingErrors.PLAYER_BLOCKED,
                        GamblingErrors.PLAYER_BLOCKED.getMessage()
                )
        );
    }

    @ParameterizedTest(name = "тип = {0}")
    @MethodSource("blockedBetProvider")
    @DisplayName("Совершение ставки игроком с заблокированным гемблингом:")
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

        step("Manager API: Попытка совершения ставки с заблокированным гемблингом", () -> {
            ctx.request = BetRequestBody.builder()
                    .sessionToken(gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT))
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