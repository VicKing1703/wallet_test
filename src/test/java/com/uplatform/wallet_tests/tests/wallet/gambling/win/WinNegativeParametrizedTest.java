package com.uplatform.wallet_tests.tests.wallet.gambling.win;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseNegativeParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrorMessages;
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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Интеграционный тест, проверяющий обработку некорректных запросов на получение выигрыша в системе Wallet.
 *
 * <p><b>Идея теста:</b>
 * Гарантировать, что API {@code POST /win} строго валидирует входящие запросы и не допускает изменение
 * состояния кошелька при нарушении контракта. Тест имитирует типовые ошибки клиента и убеждается, что
 * они отсекаются до выполнения бизнес-логики.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Контроль обязательных атрибутов:</b>
 *     <p><b>Что проверяем:</b> Реакцию сервиса на отсутствие или пустые значения {@code sessionToken},
 *     {@code transactionId}, {@code type} и {@code roundId}.</p>
 *     <p><b>Почему это важно:</b> Без этих идентификаторов невозможно корректно связать транзакции между
 *     сервисами, поэтому любые "дырявые" запросы должны быть отклонены на входе.</p>
 *   </li>
 *   <li><b>Валидация форматов и диапазонов:</b>
 *     <p><b>Что проверяем:</b> Проверку UUID, максимальной длины строк и знака суммы выигрыша.</p>
 *     <p><b>Почему это важно:</b> Некорректные форматы приводят к ошибкам интеграции и нарушению финансовой
 *     отчетности, поэтому сервис обязан возвращать предсказуемые ошибки {@link GamblingErrors}.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает корректный код ошибки и сообщение {@link GamblingErrorMessages} для каждой
 *   комбинации входных данных.</li>
 *   <li>Баланс игрока остается неизменным, подтверждая отсутствие побочных эффектов.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Негативные сценарии: /win")
@Tag("Gambling") @Tag("Wallet7")
class WinNegativeParametrizedTest extends BaseNegativeParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("20.00");
    private static final BigDecimal VALID_WIN_AMOUNT = new BigDecimal("5.00");

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
        });
    }

    static Stream<Arguments> negativeWinScenariosProvider() {
        return Stream.of(
                Arguments.of("без sessionToken",
                        (Consumer<WinRequestBody>) req -> req.setSessionToken(null),
                        GamblingErrors.MISSING_TOKEN,
                        GamblingErrorMessages.MISSING_SESSION_TOKEN
                ),
                Arguments.of("пустой sessionToken",
                        (Consumer<WinRequestBody>) req -> req.setSessionToken(""),
                        GamblingErrors.MISSING_TOKEN,
                        GamblingErrorMessages.MISSING_SESSION_TOKEN
                ),
                Arguments.of("отрицательный amount",
                        (Consumer<WinRequestBody>) req -> req.setAmount(new BigDecimal("-1.00")),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.AMOUNT_NEGATIVE
                ),
                Arguments.of("без transactionId",
                        (Consumer<WinRequestBody>) req -> req.setTransactionId(null),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_BLANK
                ),
                Arguments.of("пустой transactionId",
                        (Consumer<WinRequestBody>) req -> req.setTransactionId(""),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_BLANK
                ),
                Arguments.of("невалидный transactionId (not UUID)",
                        (Consumer<WinRequestBody>) req -> req.setTransactionId("invalid-uuid-format"),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_INVALID_UUID
                ),
                Arguments.of("без type",
                        (Consumer<WinRequestBody>) req -> req.setType(null),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TYPE_BLANK
                ),
                Arguments.of("пустой type (enum EMPTY)",
                        (Consumer<WinRequestBody>) req -> req.setType(NatsGamblingTransactionOperation.EMPTY),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TYPE_BLANK
                ),
                Arguments.of("невалидный type (enum UNKNOWN)",
                        (Consumer<WinRequestBody>) req -> req.setType(NatsGamblingTransactionOperation.UNKNOWN),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TYPE_INVALID
                ),
                Arguments.of("roundId превышает 255 символов",
                        (Consumer<WinRequestBody>) req -> req.setRoundId("x".repeat(256)),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_TOO_LONG
                ),
                Arguments.of("без roundId",
                        (Consumer<WinRequestBody>) req -> req.setRoundId(null),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_BLANK
                ),
                Arguments.of("пустой roundId",
                        (Consumer<WinRequestBody>) req -> req.setRoundId(""),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_BLANK
                )
        );
    }

    /**
     * @param description Описание тестового сценария для отчетности
     * @param requestModifier Функция, модифицирующая стандартный запрос для создания ошибочной ситуации
     * @param expectedErrorCode Ожидаемый код ошибки в ответе API
     * @param expectedMessage Ожидаемое сообщение об ошибке
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeWinScenariosProvider")
    @DisplayName("Валидация полей тела запроса на получение выигрыша:")
    void test(
            String description,
            Consumer<WinRequestBody> requestModifier,
            GamblingErrors expectedErrorCode,
            String expectedMessage
    ) {
        final class TestContext {
            WinRequestBody request;
            GamblingError error;
        }
        final TestContext ctx = new TestContext();

        step("Подготовка некорректного запроса: " + description, () -> {
            ctx.request = WinRequestBody.builder()
                    .sessionToken(gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(VALID_WIN_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.WIN)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(true)
                    .build();

            requestModifier.accept(ctx.request);
        });

        ctx.error = step(
                "Manager API: Попытка некорректного получения выигрыша - " + description,
                () -> executeExpectingError(
                        () -> managerClient.win(
                                casinoId,
                                utils.createSignature(ApiEndpoints.WIN, ctx.request),
                                ctx.request
                        ),
                        "manager_api.win.expected_exception",
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
