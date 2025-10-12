package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrorMessages;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.base.BaseNegativeParameterizedTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors.MISSING_TOKEN;
import static com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors.VALIDATION_ERROR;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Проверяет валидацию запроса роллбэка в негативных сценариях.
 *
 * <p><b>Идея теста:</b>
 * Отправлять некорректные запросы и убеждаться, что API возвращает ожидаемые ошибки.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Схема запроса:</b>
 *     <p><b>Что проверяем:</b> обязательность полей и корректность форматов.</p>
 *     <p><b>Почему это важно:</b> предотвращает обработку некорректных транзакций.</p>
 *   </li>
 *   <li><b>Коды ошибок:</b>
 *     <p><b>Что проверяем:</b> соответствие кодов и сообщений ожиданиям.</p>
 *     <p><b>Почему это важно:</b> клиенты получают понятную причину отказа.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Каждый некорректный запрос завершается ошибкой {@link GamblingErrors}.</li>
 *   <li>Сообщение полностью совпадает с константами {@link GamblingErrorMessages}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Негативные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackNegativeParametrizedTest extends BaseNegativeParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("2000.00");
    private static final BigDecimal BET_AMOUNT = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private BetRequestBody precedingBetRequestBody;
    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        step("Default Step: Регистрация нового пользователя", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение ставки для последующего роллбэка", () -> {
            var betRequest = BetRequestBody.builder()
                    .sessionToken(this.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, betRequest),
                    betRequest);

            assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
            this.precedingBetRequestBody = betRequest;
        });
    }

    static Stream<Arguments> negativeRollbackScenariosProvider() {
        return Stream.of(
                Arguments.of("без sessionToken",
                        (Consumer<RollbackRequestBody>) req -> req.setSessionToken(null),
                        MISSING_TOKEN,
                        GamblingErrorMessages.MISSING_SESSION_TOKEN),
                Arguments.of("пустой sessionToken",
                        (Consumer<RollbackRequestBody>) req -> req.setSessionToken(""),
                        MISSING_TOKEN,
                        GamblingErrorMessages.MISSING_SESSION_TOKEN),
                Arguments.of("отрицательный amount",
                        (Consumer<RollbackRequestBody>) req -> req.setAmount(new BigDecimal("-1")),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.AMOUNT_NEGATIVE_NO_LESS_THAN_ZERO),
                Arguments.of("без transactionId",
                        (Consumer<RollbackRequestBody>) req -> req.setTransactionId(null),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_BLANK),
                Arguments.of("пустой transactionId",
                        (Consumer<RollbackRequestBody>) req -> req.setTransactionId(""),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_BLANK),
                Arguments.of("невалидный transactionId (не UUID)",
                        (Consumer<RollbackRequestBody>) req -> req.setTransactionId("not-a-uuid"),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_INVALID_UUID),
                Arguments.of("без rollbackTransactionId",
                        (Consumer<RollbackRequestBody>) req -> req.setRollbackTransactionId(null),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.ROLLBACK_TRANSACTION_ID_BLANK),
                Arguments.of("пустой rollbackTransactionId",
                        (Consumer<RollbackRequestBody>) req -> req.setRollbackTransactionId(""),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.ROLLBACK_TRANSACTION_ID_BLANK),
                Arguments.of("невалидный rollbackTransactionId (не UUID)",
                        (Consumer<RollbackRequestBody>) req -> req.setRollbackTransactionId("not-a-uuid"),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.ROLLBACK_TRANSACTION_ID_INVALID_UUID_LENGTH),
                Arguments.of("roundId превышает 255 символов",
                        (Consumer<RollbackRequestBody>) req -> req.setRoundId("a".repeat(256)),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_TOO_LONG),
                Arguments.of("без roundId",
                        (Consumer<RollbackRequestBody>) req -> req.setRoundId(null),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_BLANK),
                Arguments.of("пустой roundId",
                        (Consumer<RollbackRequestBody>) req -> req.setRoundId(""),
                        VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_BLANK)
        );
    }

    /**
     * Проверяет конкретный негативный сценарий роллбэка.
     *
     * @param description описание тестового случая
     * @param requestModifier функция, модифицирующая запрос
     * @param expectedErrorCode ожидаемый код ошибки
     * @param expectedMessage ожидаемое сообщение
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeRollbackScenariosProvider")
    @DisplayName("Негативный сценарий получения роллбэка")
    void test(
            String description,
            Consumer<RollbackRequestBody> requestModifier,
            GamblingErrors expectedErrorCode,
            String expectedMessage
    ) {
        final class TestContext {
            RollbackRequestBody requestBody;
            GamblingError error;
        }
        final TestContext ctx = new TestContext();

        step("Подготовка некорректного запроса: " + description, () -> {
            ctx.requestBody = RollbackRequestBody.builder()
                    .sessionToken(this.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(this.precedingBetRequestBody.getTransactionId())
                    .roundId(this.precedingBetRequestBody.getRoundId())
                    .roundClosed(true)
                    .playerId(this.registeredPlayer.walletData().walletUUID())
                    .currency(this.registeredPlayer.walletData().currency())
                    .gameUuid(this.gameLaunchData.dbGameSession().getGameUuid())
                    .build();

            requestModifier.accept(ctx.requestBody);
        });

        ctx.error = step(
                "Manager API: Попытка некорректного роллбэка - " + description,
                () -> executeExpectingError(
                        () -> managerClient.rollback(
                                casinoId,
                                utils.createSignature(ApiEndpoints.ROLLBACK, ctx.requestBody),
                                ctx.requestBody
                        ),
                        "manager_api.rollback.expected_exception",
                        GamblingError.class
                )
        );

        assertValidationError(
                ctx.error,
                expectedErrorCode.getCode(),
                expectedMessage
        );
    }
}
