package com.uplatform.wallet_tests.tests.wallet.gambling.refund;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
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
 * Проверяет рефанд при заблокированном гемблинге.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что блокировка гемблинга не мешает возврату казино-ставок.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>CAP блокировки:</b>
 *     <p><b>Что проверяем:</b> установка флагов {@code gamblingEnabled=false}, {@code bettingEnabled=true}.</p>
 *     <p><b>Почему это важно:</b> позволяет воспроизвести сценарий ограничения гемблинга.</p>
 *   </li>
 *   <li><b>Refund API:</b>
 *     <p><b>Что проверяем:</b> статус {@code 200 OK} и расчет баланса.</p>
 *     <p><b>Почему это важно:</b> возврат должен быть доступен даже при запрете гемблинга.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>CAP API успешно блокирует гемблинг.</li>
 *   <li>Рефанд завершается успехом для всех типов ставок.</li>
 *   <li>Баланс игрока возвращается к исходному.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/refund")
@Suite("Позитивные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet7")
class RefundWhenGamblingBlockedParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> refundScenarioProvider() {
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
     * Проверяет рефанд при заблокированном гемблинге.
     *
     * <p><b>Идея теста:</b>
     * Совершить ставку, заблокировать гемблинг и выполнить возврат.</p>
     *
     * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
     * <ul>
     *   <li><b>CAP API:</b>
     *     <p><b>Что проверяем:</b> корректное применение блокировки.</p>
     *     <p><b>Почему это важно:</b> необходимо воспроизвести бизнес-ограничение.</p>
     *   </li>
     *   <li><b>Refund API:</b>
     *     <p><b>Что проверяем:</b> статус {@code 200 OK} и расчет баланса.</p>
     *     <p><b>Почему это важно:</b> возврат должен выполняться даже при запрете гемблинга.</p>
     *   </li>
     * </ul>
     *
     * <p><b>Ожидаемые результаты:</b></p>
     * <ul>
     *   <li>Блокировка гемблинга применяется успешно.</li>
     *   <li>Рефанд возвращает статус {@code 200 OK}.</li>
     *   <li>Баланс после рефанда совпадает с исходным.</li>
     * </ul>
     *
     * @param betAmountParam сумма ставки (может быть {@code 0})
     * @param typeParam тип исходной операции ставки ({@link NatsGamblingTransactionOperation})
     */
    @ParameterizedTest(name = "тип = {1}, сумма = {0}")
    @MethodSource("refundScenarioProvider")
    @DisplayName("Получение рефанда игроком с заблокированным гемблингом (беттинг разрешен):")
    void test(BigDecimal betAmountParam, NatsGamblingTransactionOperation typeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            BigDecimal expectedBalanceAfterRefund;
        }
        final TestContext ctx = new TestContext();
        ctx.expectedBalanceAfterRefund = INITIAL_ADJUSTMENT_AMOUNT;

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение исходной транзакции (ставки)", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(typeParam)
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

        step("Manager API: Выполнение рефанда транзакции", () -> {
            var refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.betRequestBody.getTransactionId())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .build();

            var response = managerClient.refund(
                    casinoId,
                    utils.createSignature(ApiEndpoints.REFUND, refundRequestBody),
                    refundRequestBody);

            assertNotNull(response.getBody(), "manager_api.refund.body_not_null");
            assertAll("Проверка статус-кода и тела ответа при рефанде",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code"),
                    () -> assertEquals(refundRequestBody.getTransactionId(), response.getBody().transactionId(), "manager_api.refund.transaction_id"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterRefund.compareTo(response.getBody().balance()), "manager_api.refund.balance")
            );
        });
    }
}