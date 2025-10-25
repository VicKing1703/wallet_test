package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Проверяет возможность роллбэка при заблокированном гемблинге и разрешённом беттинге.
 *
 * <p><b>Идея теста:</b>
 * Совершить ставку, заблокировать гемблинг и убедиться, что отмена ставки по-прежнему доступна.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Блокировка через CAP:</b>
 *     <p><b>Что проверяем:</b> корректность применения флага {@code gamblingEnabled=false}.</p>
 *     <p><b>Почему это важно:</b> подтверждает консистентность ограничений игрока.</p>
 *   </li>
 *   <li><b>Роллбэк ставки:</b>
 *     <p><b>Что проверяем:</b> успешный ответ {@code /rollback} после блокировки.</p>
 *     <p><b>Почему это важно:</b> отмена операции должна быть доступна независимо от блокировки гемблинга.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Ставка завершается статусом {@code 200 OK}.</li>
 *   <li>CAP возвращает статус {@code 204 NO CONTENT} при блокировке гемблинга.</li>
 *   <li>Роллбэк завершается статусом {@code 200 OK}, баланс совпадает с исходным.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Позитивные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet7")
class RollbackWhenGamblingBlockedParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> rollbackScenarioProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN
                )
        );
    }

    /**
     * Выполняет роллбэк при заблокированном гемблинге.
     *
     * @param betAmountParam сумма исходной ставки
     * @param betTypeParam тип операции
     */
    @ParameterizedTest(name = "тип исходной ставки = {1}, сумма = {0}")
    @MethodSource("rollbackScenarioProvider")
    @DisplayName("Роллбэк при заблокированном гемблинге")
    void testRollbackWhenGamblingBlocked(
            BigDecimal betAmountParam,
            NatsGamblingTransactionOperation betTypeParam
    ) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            BigDecimal expectedBalanceAfterRollback;
        }
        final TestContext ctx = new TestContext();
        ctx.expectedBalanceAfterRollback = INITIAL_ADJUSTMENT_AMOUNT;

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение исходной ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(betTypeParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("CAP API: Блокировка гемблинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(false)
                    .bettingEnabled(true)
                    .build();

            var response = capAdminClient.updateBlockers(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_blockers.status_code");
        });

        step("Manager API: Роллбэк исходной ставки", () -> {
            var rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(ctx.betRequestBody.getTransactionId())
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.rollback(
                    casinoId,
                    utils.createSignature(ApiEndpoints.ROLLBACK, rollbackRequestBody),
                    rollbackRequestBody);

            assertNotNull(response.getBody(), "manager_api.rollback.body_not_null");
            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code"),
                    () -> assertEquals(rollbackRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.rollback.transaction_id"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterRollback.compareTo(response.getBody().balance()), "manager_api.rollback.balance")
            );
        });
    }
}
