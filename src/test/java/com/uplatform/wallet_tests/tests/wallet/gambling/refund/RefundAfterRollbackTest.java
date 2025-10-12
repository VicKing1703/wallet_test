package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
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
 * Проверяет невозможность рефанда после роллбэка ставки.
 *
 * <p><b>Идея теста:</b>
 * Совершить ставку, выполнить роллбэк и убедиться, что повторное обращение к {@code /refund} отклоняется.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Роллбэк ставки:</b>
 *     <p><b>Что проверяем:</b> успешное выполнение {@code /rollback} и корректный статус ответа.</p>
 *     <p><b>Почему это важно:</b> роллбэк должен восстанавливать баланс и блокировать дальнейшие возвраты.</p>
 *   </li>
 *   <li><b>Отказ рефанда:</b>
 *     <p><b>Что проверяем:</b> ошибка {@link GamblingErrors#REFUND_NOT_ALLOWED} при попытке рефанда.</p>
 *     <p><b>Почему это важно:</b> предотвращает повторное возмещение средств.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Роллбэк завершается успешно.</li>
 *   <li>Рефанд возвращает {@code 400 BAD REQUEST}.</li>
 *   <li>Код ошибки соответствует {@code REFUND_NOT_ALLOWED}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Негативные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundAfterRollbackTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal BET_AMOUNT = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    /**
     * Выполняет роллбэк ставки и проверяет отказ в рефанде.
     *
     * <p><b>Идея теста:</b>
     * Убедиться, что после роллбэка система запрещает рефанд исходной ставки.</p>
     *
     * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
     * <ul>
     *   <li><b>Rollback API:</b>
     *     <p><b>Что проверяем:</b> статус {@code 200 OK} и корректное тело ответа.</p>
     *     <p><b>Почему это важно:</b> роллбэк должен восстановить баланс и закрыть возможность повторного возврата.</p>
     *   </li>
     *   <li><b>Refund API:</b>
     *     <p><b>Что проверяем:</b> ошибку {@code REFUND_NOT_ALLOWED} при повторном запросе.</p>
     *     <p><b>Почему это важно:</b> гарантирует, что клиент не получит двойное возмещение.</p>
     *   </li>
     * </ul>
     *
     * <p><b>Ожидаемые результаты:</b></p>
     * <ul>
     *   <li>Роллбэк выполняется успешно.</li>
     *   <li>Рефанд завершается ошибкой {@code 400 BAD REQUEST}.</li>
     *   <li>Ответ содержит код и сообщение {@code REFUND_NOT_ALLOWED}.</li>
     * </ul>
     */
    @Test
    @DisplayName("Попытка рефанда после роллбэка ставки")
    void testRefundAfterRollbackReturnsError() {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RollbackRequestBody rollbackRequestBody;
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

        step("Manager API: Выполнение роллбэка для ставки", () -> {
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

        step("Manager API: Попытка выполнения рефанда для ставки, по которой был роллбэк", () -> {
            RefundRequestBody refundAttemptRequestBody = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.betRequestBody.getTransactionId())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.refund(
                            casinoId,
                            utils.createSignature(ApiEndpoints.REFUND, refundAttemptRequestBody),
                            refundAttemptRequestBody
                    ),
                    "manager_api.refund.after_rollback.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("manager_api.refund.after_rollback.error_validation",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.refund.status_code"),
                    () -> assertNotNull(error, "manager_api.refund.body"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getCode(), error.code(), "manager_api.refund.error_code"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getMessage(), error.message(), "manager_api.refund.error_message")
            );
        });
    }
}