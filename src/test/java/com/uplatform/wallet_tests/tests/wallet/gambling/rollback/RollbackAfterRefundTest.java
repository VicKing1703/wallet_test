package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
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
 * Проверяет отказ выполнения роллбэка после успешного рефанда той же ставки.
 *
 * <p><b>Идея теста:</b>
 * Совершить ставку, выполнить рефанд и убедиться, что повторный возврат через {@code /rollback} блокируется.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Первичный возврат:</b>
 *     <p><b>Что проверяем:</b> статус {@code 200 OK} при рефанде.</p>
 *     <p><b>Почему это важно:</b> подтверждает базовую корректность сценария рефанда.</p>
 *   </li>
 *   <li><b>Попытка роллбэка:</b>
 *     <p><b>Что проверяем:</b> ошибку {@link GamblingErrors#ROLLBACK_NOT_ALLOWED}.</p>
 *     <p><b>Почему это важно:</b> исключает двойное возмещение средств.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Ставка и рефанд завершаются успешно.</li>
 *   <li>Роллбэк возвращает ошибку {@code 400 BAD REQUEST}.</li>
 *   <li>Код и сообщение ошибки соответствуют {@code ROLLBACK_NOT_ALLOWED}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Негативные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackAfterRefundTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal BET_AMOUNT = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    /**
     * Выполняет попытку роллбэка после успешного рефанда и проверяет ожидаемую ошибку.
     */
    @Test
    @DisplayName("Попытка роллбэка ставки после выполнения рефанда по ней")
    void testRollbackAfterRefundReturnsError() {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RefundRequestBody refundRequestBody;
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

        step("Manager API: Выполнение рефанда по ставке", () -> {
            ctx.refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
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
                    utils.createSignature(ApiEndpoints.REFUND, ctx.refundRequestBody),
                    ctx.refundRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code");
        });

        step("Manager API: Попытка выполнения роллбэка для ставки с рефандом", () -> {
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

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.rollback(
                            casinoId,
                            utils.createSignature(ApiEndpoints.ROLLBACK, ctx.rollbackRequestBody),
                            ctx.rollbackRequestBody
                    ),
                    "manager_api.rollback_after_refund.expected_exception"
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
