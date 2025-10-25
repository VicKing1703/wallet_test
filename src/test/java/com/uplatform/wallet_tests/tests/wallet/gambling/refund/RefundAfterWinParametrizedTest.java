package com.uplatform.wallet_tests.tests.wallet.gambling.refund;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Проверяет отклонение рефанда для транзакций начисления выигрыша.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что система блокирует возврат средств по операциям WIN, JACKPOT и FREESPIN,
 * поскольку они не являются ставками и не должны рефандиться.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Начисление выигрыша:</b>
 *     <p><b>Что проверяем:</b> успешное выполнение запроса {@code /win} и корректный статус ответа.</p>
 *     <p><b>Почему это важно:</b> базовый сценарий должен выполняться без ошибок до шага рефанда.</p>
 *   </li>
 *   <li><b>Запрет рефанда:</b>
 *     <p><b>Что проверяем:</b> ответ {@code /refund} с ошибкой {@link GamblingErrors#REFUND_NOT_ALLOWED}.</p>
 *     <p><b>Почему это важно:</b> предотвращает двойное начисление средств игроку.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос {@code /win} возвращает {@code 200 OK} для всех типов операций.</li>
 *   <li>Запрос {@code /refund} завершается ошибкой {@code 400 BAD REQUEST} с кодом {@code REFUND_NOT_ALLOWED}.</li>
 *   <li>Тело ошибки содержит ожидаемое сообщение и код.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Негативные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet7")
class RefundAfterWinParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> winLikeTransactionTypeProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.WIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.JACKPOT
                ),
                Arguments.of(
                        generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT),
                        NatsGamblingTransactionOperation.FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.WIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.JACKPOT
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN
                )
        );
    }

    /**
     * Имитирует начисление выигрыша и проверяет невозможность рефанда.
     *
     * <p><b>Идея теста:</b>
     * Совершить выигрыш выбранного типа и убедиться, что попытка рефанда завершается ошибкой.</p>
     *
     * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
     * <ul>
     *   <li><b>HTTP API:</b>
     *     <p><b>Что проверяем:</b> статусы и тело ошибок для вызовов {@code /win} и {@code /refund}.</p>
     *     <p><b>Почему это важно:</b> корректная обработка исключений исключает нарушение финансовой логики.</p>
     *   </li>
     *   <li><b>Сообщение об ошибке:</b>
     *     <p><b>Что проверяем:</b> код {@code REFUND_NOT_ALLOWED} и соответствующее сообщение.</p>
     *     <p><b>Почему это важно:</b> пользователи и интеграции получают однозначную причину отказа.</p>
     *   </li>
     * </ul>
     *
     * <p><b>Ожидаемые результаты:</b></p>
     * <ul>
     *   <li>Выигрыш фиксируется успешно.</li>
     *   <li>Рефанд завершается ошибкой {@code 400 BAD REQUEST}.</li>
     *   <li>Ответ содержит код {@code REFUND_NOT_ALLOWED} и ожидаемое сообщение.</li>
     * </ul>
     *
     * @param winAmountParam сумма исходной транзакции выигрыша (может быть {@code 0})
     * @param winOperationTypeParam тип операции выигрыша ({@link NatsGamblingTransactionOperation})
     */
    @ParameterizedTest(name = "транзакция типа [{1}] суммой [{0}] должна вызвать ошибку")
    @MethodSource("winLikeTransactionTypeProvider")
    @DisplayName("Попытка рефанда:")
    void test(BigDecimal winAmountParam, NatsGamblingTransactionOperation winOperationTypeParam) {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            WinRequestBody winRequestBody;
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

        step("Manager API: Совершение исходной транзакции выигрыша", () -> {
            ctx.winRequestBody = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(winOperationTypeParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.winRequestBody),
                    ctx.winRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code");
        });

        step("Manager API: Попытка выполнения рефанда для транзакции выигрыша", () -> {
            var refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.winRequestBody.getTransactionId())
                    .roundId(ctx.winRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.refund(
                            casinoId,
                            utils.createSignature(ApiEndpoints.REFUND, refundRequestBody),
                            refundRequestBody
                    ),
                    "manager_api.refund_after_win.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("manager_api.refund.after_win.error_validation",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.refund.status_code"),
                    () -> assertNotNull(error, "manager_api.refund.body"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getCode(), error.code(), "manager_api.refund.error_code"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getMessage(), error.message(), "manager_api.refund.error_message")
            );
        });
    }
}