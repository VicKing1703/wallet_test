package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
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
 * Проверяет отказ системы при повторном запросе рефанда.
 *
 * <p><b>Идея теста:</b>
 * Совершить ставку, выполнить успешный рефанд и убедиться, что повторная попытка возвращает управляемую ошибку.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Первый рефанд:</b>
 *     <p><b>Что проверяем:</b> статус {@code 200 OK} и корректные данные ответа.</p>
 *     <p><b>Почему это важно:</b> подтверждает базовую работоспособность сценария возврата.</p>
 *   </li>
 *   <li><b>Повторный запрос:</b>
 *     <p><b>Что проверяем:</b> ошибку {@link GamblingErrors#REFUND_NOT_ALLOWED} при повторном вызове.</p>
 *     <p><b>Почему это важно:</b> исключает двойное возмещение по одной ставке.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Исходная ставка и первый рефанд выполняются успешно.</li>
 *   <li>Повторный рефанд завершается ошибкой {@code 400 BAD REQUEST}.</li>
 *   <li>Код и сообщение ошибки совпадают с {@code REFUND_NOT_ALLOWED}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Негативные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundAfterRefundTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal BET_AMOUNT = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    /**
     * Повторяет запрос рефанда для уже возвращенной ставки.
     *
     * <p><b>Идея теста:</b>
     * Проверить, что повторное обращение к {@code /refund} приводит к предсказуемой ошибке.</p>
     *
     * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
     * <ul>
     *   <li><b>API-ответ:</b>
     *     <p><b>Что проверяем:</b> статусы и тело ответа для первой и повторной попытки.</p>
     *     <p><b>Почему это важно:</b> дублирующие запросы не должны менять баланс.</p>
     *   </li>
     *   <li><b>Сообщение об ошибке:</b>
     *     <p><b>Что проверяем:</b> код {@code REFUND_NOT_ALLOWED}.</p>
     *     <p><b>Почему это важно:</b> прозрачное объяснение причины отказа.</p>
     *   </li>
     * </ul>
     *
     * <p><b>Ожидаемые результаты:</b></p>
     * <ul>
     *   <li>Первый вызов {@code /refund} успешен.</li>
     *   <li>Повторный вызов завершается ошибкой {@code 400 BAD REQUEST}.</li>
     *   <li>Ответ содержит код и сообщение {@code REFUND_NOT_ALLOWED}.</li>
     * </ul>
     */
    @Test
    @DisplayName("Повторная попытка рефанда с теми же параметрами")
    void testRefundAfterRefundReturnsError() {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RefundRequestBody refundRequestBody;
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

        step("Manager API: Выполнение первого (успешного) рефанда", () -> {
            ctx.refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.betRequestBody.getTransactionId())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.refund(
                    casinoId,
                    utils.createSignature(ApiEndpoints.REFUND, ctx.refundRequestBody),
                    ctx.refundRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code");
        });

        step("Manager API: Повторное выполнение рефанда с теми же параметрами", () -> {
            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.refund(
                            casinoId,
                            utils.createSignature(ApiEndpoints.REFUND, ctx.refundRequestBody),
                            ctx.refundRequestBody
                    ),
                    "manager_api.refund.double.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("manager_api.refund.double.error_validation",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.refund.status_code"),
                    () -> assertNotNull(error, "manager_api.refund.body"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getCode(), error.code(), "manager_api.refund.error_code"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getMessage(), error.message(), "manager_api.refund.error_message")
            );
        });
    }
}