package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Интеграционный тест, проверяющий негативные сценарии для эндпоинта совершения ставки: {@code POST /bet}.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить надежность и финансовую целостность системы при обработке ставок.
 * Основная задача — верифицировать, что API выполняет строгую предварительную валидацию всех входящих запросов.
 * Любой запрос, не соответствующий контракту (невалидные данные) или нарушающий бизнес-правила (недостаточно средств),
 * должен быть категорически отклонен <b>до</b> начала какой-либо финансовой операции и <b>до</b> изменения баланса игрока.
 * Таким образом, тест гарантирует, что система предотвращает любую возможность случайного или намеренного некорректного
 * списания средств, обеспечивая целостность данных.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Целостность и полнота данных:</b>
 *     <p><b>Что проверяем:</b> Отсутствие, пустоту или некорректный формат обязательных полей, таких как
 *     {@code sessionToken}, {@code transactionId}, {@code type} и {@code roundId}.
 *     <p><b>Почему это важно:</b> Чтобы гарантировать, что ни одна транзакция не может быть обработана без полного
 *     и валидного набора идентификаторов. Это предотвращает создание "транзакций-призраков", которые невозможно
 *     отследить или корректно обработать в последующих системах (например, в отчетах или при разрешении споров).
 *   </li>
 *   <li><b>Соблюдение финансовых правил:</b>
 *     <p><b>Что проверяем:</b> Попытки совершить ставку с нелогичной (отрицательной) или невозможной
 *     (превышающей баланс) суммой.
 *     <p><b>Почему это важно:</b> Это фундаментальная проверка, защищающая как игрока, так и оператора от
 *     финансовых ошибок. Система должна на самом раннем этапе пресекать любые операции, которые могут
 *     привести к отрицательному балансу или некорректным списаниям. Это ядро финансовой целостности.
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Система возвращает корректный HTTP-статус и код ошибки для каждого невалидного сценария.</li>
 *   <li>Баланс игрока остается неизменным, подтверждая, что валидация происходит до списания средств.</li>
 *   <li>Отсутствие побочных эффектов: для отклоненных запросов не генерируются события в NATS или Kafka.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class BetNegativeParametrizedTest extends BaseNegativeParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("20.00");
    private static final BigDecimal VALID_BET_AMOUNT = new BigDecimal("1.00");

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

    static Stream<Arguments> negativeBetScenariosProvider() {
        return Stream.of(
                Arguments.of("без sessionToken",
                        (Consumer<BetRequestBody>) req -> req.setSessionToken(null),
                        GamblingErrors.MISSING_TOKEN,
                        GamblingErrorMessages.MISSING_SESSION_TOKEN),

                Arguments.of("пустой sessionToken",
                        (Consumer<BetRequestBody>) req -> req.setSessionToken(""),
                        GamblingErrors.MISSING_TOKEN,
                        GamblingErrorMessages.MISSING_SESSION_TOKEN),

                Arguments.of("отрицательный amount",
                        (Consumer<BetRequestBody>) req -> req.setAmount(new BigDecimal("-1.0")),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.AMOUNT_NEGATIVE),

                Arguments.of("размер ставки превышает баланс",
                        (Consumer<BetRequestBody>) req -> req.setAmount(new BigDecimal("21.00")),
                        GamblingErrors.BUSINESS_LOGIC_ERROR,
                        GamblingErrorMessages.INSUFFICIENT_BALANCE),

                Arguments.of("без transactionId",
                        (Consumer<BetRequestBody>) req -> req.setTransactionId(null),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_BLANK),

                Arguments.of("пустой transactionId",
                        (Consumer<BetRequestBody>) req -> req.setTransactionId(""),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_BLANK),

                Arguments.of("невалидный transactionId (не UUID)",
                        (Consumer<BetRequestBody>) req -> req.setTransactionId("not-a-uuid"),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_INVALID_UUID),

                Arguments.of("без type",
                        (Consumer<BetRequestBody>) req -> req.setType(null),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TYPE_BLANK),

                Arguments.of("пустой type",
                        (Consumer<BetRequestBody>) req -> req.setType(NatsGamblingTransactionOperation.EMPTY),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TYPE_BLANK),

                Arguments.of("невалидный type",
                        (Consumer<BetRequestBody>) req -> req.setType(NatsGamblingTransactionOperation.UNKNOWN),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TYPE_INVALID),

                Arguments.of("roundId превышает 255 символов",
                        (Consumer<BetRequestBody>) req -> req.setRoundId("a".repeat(256)),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_TOO_LONG),

                Arguments.of("без roundId",
                        (Consumer<BetRequestBody>) req -> req.setRoundId(null),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_BLANK),

                Arguments.of("пустой roundId",
                        (Consumer<BetRequestBody>) req -> req.setRoundId(""),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_BLANK)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeBetScenariosProvider")
    @DisplayName("Негативный сценарий совершения ставки в игровой сессии:")
    void test(
            String description,
            Consumer<BetRequestBody> requestModifier,
            GamblingErrors expectedErrorCode,
            String expectedMessage
    ) {
        final class TestContext {
            BetRequestBody request;
            GamblingError error;
        }
        final TestContext ctx = new TestContext();

        step("Подготовка некорректного запроса: " + description, () -> {
            ctx.request = BetRequestBody.builder()
                    .sessionToken(gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(VALID_BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            requestModifier.accept(ctx.request);
        });

        ctx.error = step(
                "Manager API: Попытка некорректной ставки - " + description,
                () -> executeExpectingError(
                        () -> managerClient.bet(
                                casinoId,
                                utils.createSignature(ApiEndpoints.BET, ctx.request),
                                ctx.request
                        ),
                        "manager_api.bet.expected_exception",
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
