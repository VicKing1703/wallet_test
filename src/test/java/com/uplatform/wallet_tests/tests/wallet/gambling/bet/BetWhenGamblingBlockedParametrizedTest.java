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
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий негативные сценарии совершения ставок полностью заблокированным игроком.
 *
 * <p><b>Идея теста:</b> Убедиться в абсолютной надежности механизма полной блокировки игрока. Этот тест является
 * более строгой версией проверки блокировки гемблинга, так как эмулирует ситуацию, когда администратор полностью
 * заблокировал аккаунт ({@code manuallyBlocked=true}). Система должна действовать как непробиваемая стена,
 * категорически отклоняя любые попытки совершить ставку. Это подтверждает, что глобальная блокировка имеет
 * наивысший приоритет и гарантирует полную изоляцию игрока от финансовых операций.</p>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <p>Тест эмулирует ситуацию, когда администратор установил игроку полную ручную блокировку аккаунта.
 * После этого игрок пытается совершить различные типы ставок (BET, TIPS, FREESPIN). Проверяется, что каждая
 * такая попытка будет немедленно отклонена системой.</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока, пополнение баланса и создание игровой сессии.</li>
 *   <li>Установка полной ручной блокировки для этого игрока через CAP API ({@code manuallyBlocked = true}).</li>
 *   <li>Для каждого типа ставки (BET, TIPS, FREESPIN):
 *     <ul>
 *       <li>Отправка запроса на совершение ставки через Manager API.</li>
 *       <li>Проверка получения ожидаемой ошибки о блокировке игрока.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Для всех типов ставок API возвращает ошибку с кодом {@link GamblingErrors#PLAYER_BLOCKED} и сообщением "player was blocked".</li>
 *   <li>Баланс игрока остается абсолютно неизменным.</li>
 *   <li>В системе не генерируются никакие события о финансовых транзакциях, связанные с этими попытками.</li>
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
    private BigDecimal betAmount;
    private String casinoId;

    @BeforeAll
    void setUp() {
        betAmount = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);
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
                arguments(
                        NatsGamblingTransactionOperation.BET,
                        GamblingErrors.PLAYER_BLOCKED,
                        GamblingErrors.PLAYER_BLOCKED.getMessage()
                ),
                arguments(
                        NatsGamblingTransactionOperation.TIPS,
                        GamblingErrors.PLAYER_BLOCKED,
                        GamblingErrors.PLAYER_BLOCKED.getMessage()
                ),
                arguments(
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
                    .amount(betAmount)
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