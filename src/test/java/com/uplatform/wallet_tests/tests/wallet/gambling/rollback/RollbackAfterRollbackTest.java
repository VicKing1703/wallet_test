package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Проверяет отказ системы при повторном роллбэке одной и той же ставки.
 *
 * <p><b>Идея теста:</b>
 * Совершить ставку, выполнить успешный роллбэк и убедиться, что повторная попытка возвращает ошибку.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Первый роллбэк:</b>
 *     <p><b>Что проверяем:</b> статус {@code 200 OK} и корректные данные ответа.</p>
 *     <p><b>Почему это важно:</b> подтверждает возможность отмены ставки.</p>
 *   </li>
 *   <li><b>Повторный запрос:</b>
 *     <p><b>Что проверяем:</b> ошибку {@link GamblingErrors#ROLLBACK_NOT_ALLOWED}.</p>
 *     <p><b>Почему это важно:</b> исключает двойной возврат средств.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Ставка и первый роллбэк завершаются успешно.</li>
 *   <li>Повторный роллбэк завершаетcя ошибкой {@code 400 BAD REQUEST}.</li>
 *   <li>Ответ содержит код и сообщение {@code ROLLBACK_NOT_ALLOWED}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Негативные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackAfterRollbackTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal BET_AMOUNT = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    /**
     * Повторяет запрос роллбэка после успешной отмены и проверяет ожидаемую ошибку.
     */
    @Test
    @DisplayName("Попытка повторного роллбэка ставки")
    void testRepeatedRollbackReturnsError() {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RollbackRequestBody rollbackRequestBody;
            GamblingError error;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("Manager API: Выполнение первого роллбэка", () -> {
            ctx.rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
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
                    utils.createSignature(ApiEndpoints.ROLLBACK, ctx.rollbackRequestBody),
                    ctx.rollbackRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code");
        });

        step("Manager API: Попытка повторного роллбэка", () -> {
            var repeatedRequest = RollbackRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(ctx.betRequestBody.getTransactionId())
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.rollback(
                            casinoId,
                            utils.createSignature(ApiEndpoints.ROLLBACK, repeatedRequest),
                            repeatedRequest
                    ),
                    "manager_api.rollback.repeated.expected_exception"
            );

            ctx.error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll(
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.rollback.status_code"),
                    () -> assertNotNull(ctx.error, "manager_api.rollback.body"),
                    () -> assertEquals(GamblingErrors.ROLLBACK_NOT_ALLOWED.getCode(), ctx.error.code(), "manager_api.rollback.error_code"),
                    () -> assertEquals(GamblingErrors.ROLLBACK_NOT_ALLOWED.getMessage(), ctx.error.message(), "manager_api.rollback.error_message")
            );
        });
    }
}
