package com.uplatform.wallet_tests.tests.wallet.admin;

import com.uplatform.wallet_tests.tests.base.BaseNegativeParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий негативные сценарии для эндпоинта ручной корректировки баланса:
 * {@code POST /_cap/api/v1/wallet/{playerUUID}/create-balance-adjustment}.
 *
 * <p><b>Идея теста:</b> Обеспечить максимальную надежность и безопасность операций прямого изменения баланса игрока.
 * API должен быть абсолютно строг к входящим данным и бизнес-правилам, чтобы исключить любую возможность
 * случайного или намеренного некорректного изменения средств. Тест проверяет, что система отклоняет все
 * сомнительные запросы и не производит никаких финансовых транзакций в этих случаях.</p>
 *
 * <p><b>Сценарии тестирования сгруппированы по типу ошибки:</b></p>
 * <ol>
 *   <li><b>Ошибки валидации тела запроса (HTTP 400):</b>
 *     <ul>
 *       <li>Отправка запроса с отсутствующими (null), пустыми или некорректными значениями для всех полей:
 *           {@code amount}, {@code currency}, {@code reason}, {@code operationType}, {@code direction}.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ошибки бизнес-логики (HTTP 400):</b>
 *     <ul>
 *       <li>Попытка списания суммы, превышающей текущий баланс игрока.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ошибки в параметрах запроса (HTTP 400, 404):</b>
 *     <ul>
 *       <li>Использование несуществующего или невалидного по формату {@code playerUUID}.</li>
 *       <li>Указание валюты ({@code currency}), которая не соответствует валюте кошелька игрока.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ошибки аутентификации (HTTP 401):</b>
 *     <ul>
 *       <li>Отправка запроса с отсутствующим или пустым заголовком {@code Authorization}.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Система возвращает корректный HTTP-статус ошибки (400, 401, 404) для каждого сценария.</li>
 *   <li>Тело ответа содержит правильное сообщение об ошибке и детали валидации в поле {@code errors}.</li>
 *   <li>Баланс игрока остается абсолютно неизменным после любого из этих невалидных запросов.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Управление игроком")
@Suite("Ручная корректировка баланса: Негативные сценарии")
@Tag("Wallet") @Tag("CAP")
class BalanceAdjustmentNegativeParametrizedTest extends BaseNegativeParameterizedTest {

    private static final BigDecimal VALID_ADJUSTMENT_AMOUNT = new BigDecimal("50.00");
    private static final String REQUEST_COMMENT = "Balance adjustment negative test";

    private RegisteredPlayerData registeredPlayer;
    private String platformNodeId;

    @BeforeEach
    void setUp() {
        platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(registeredPlayer, "default_step.registration");
        });
    }

    static Stream<Arguments> requestBodyValidationScenarios() {
        return Stream.of(
                arguments(
                        "Параметр тела amount: отсутствует",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.amount(null),
                        "Validation error",
                        Map.of("amount", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела amount: отрицательный",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.amount(new BigDecimal("-50.00")),
                        "amount must be positive",
                        Map.of()
                ),
                arguments(
                        "Параметр тела currency: отсутствует",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.currency(null),
                        "Validation error",
                        Map.of("currency", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела currency: пустая строка",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.currency(""),
                        "Validation error",
                        Map.of("currency", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела reason: отсутствует",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.reason(null),
                        "Validation error",
                        Map.of("reason", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела reason: пустая строка",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.reason(ReasonType.EMPTY),
                        "Validation error",
                        Map.of("reason", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела reason: неизвестный",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.reason(ReasonType.UNKNOWN),
                        "Validation error",
                        Map.of("reason", List.of("value.out.of.list"))
                ),
                arguments(
                        "Параметр тела operationType: отсутствует",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.operationType(null),
                        "Validation error",
                        Map.of("operationType", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела operationType: пустая строка",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.operationType(OperationType.EMPTY),
                        "Validation error",
                        Map.of("operationType", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела operationType: неизвестный",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.operationType(OperationType.UNKNOWN),
                        "Validation error",
                        Map.of("operationType", List.of("value.out.of.list"))
                ),
                arguments(
                        "Параметр тела direction: отсутствует",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.direction(null),
                        "Validation error",
                        Map.of("direction", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела direction: пустая строка",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.direction(DirectionType.EMPTY),
                        "Validation error",
                        Map.of("direction", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела direction: неизвестный",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.direction(DirectionType.UNKNOWN),
                        "Validation error",
                        Map.of("direction", List.of("value.out.of.list"))
                )
        );
    }

    static Stream<Arguments> businessRuleViolationScenarios() {
        return Stream.of(
                arguments(
                        "Параметр тела amount: превышает баланс",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder
                                .amount(new BigDecimal("5000.00"))
                                .direction(DirectionType.DECREASE),
                        "balance is not enough",
                        Map.of()
                )
        );
    }

    static Stream<Arguments> requestParameterValidationScenarios() {
        return Stream.of(
                arguments(
                        "Параметр пути playerUuid: рандомный UUID",
                        (Function<RegisteredPlayerData, String>) playerData -> UUID.randomUUID().toString(),
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> {},
                        404,
                        "Player not found",
                        Map.of()
                ),
                arguments(
                        "Параметр пути playerUuid: невалидный формат UUID",
                        (Function<RegisteredPlayerData, String>) playerData -> "not-a-uuid",
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> {},
                        404,
                        "Not found",
                        Map.of()
                ),
                arguments(
                        "Параметр тела currency: не совпадает с валютой игрока",
                        (Function<RegisteredPlayerData, String>) playerData -> playerData.walletData().playerUUID(),
                        (Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder>) builder -> builder.currency("BTC"),
                        404,
                        "Player not found",
                        Map.of()
                )
        );
    }

    static Stream<Arguments> authorizationErrorScenarios() {
        return Stream.of(
                arguments("Заголовок Authorization: отсутствие заголовка", null),
                arguments("Заголовок Authorization: пустая строка", "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestBodyValidationScenarios")
    @DisplayName("Создание корректировки баланса: ошибки валидации тела запроса")
    void shouldReturnBadRequestWhenRequestBodyInvalid(
            String description,
            Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder> requestCustomizer,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors
    ) {
        var requestBuilder = baseRequestBuilder();
        requestCustomizer.accept(requestBuilder);
        var request = requestBuilder.build();

        var error = step(
                "CAP API: Попытка создания корректировки баланса с некорректным телом запроса - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.createBalanceAdjustment(
                                registeredPlayer.walletData().playerUUID(),
                                utils.getAuthorizationHeader(),
                                platformNodeId,
                                UUID.randomUUID().toString(),
                                request
                        ),
                        "cap_api.create_balance_adjustment.expected_exception"
                )
        );

        assertValidationError(error, 400, expectedMessage, expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("businessRuleViolationScenarios")
    @DisplayName("Создание корректировки баланса: ошибки бизнес-логики")
    void shouldReturnBadRequestWhenBusinessRulesViolated(
            String description,
            Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder> requestCustomizer,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors
    ) {
        var requestBuilder = baseRequestBuilder();
        requestCustomizer.accept(requestBuilder);
        var request = requestBuilder.build();

        var error = step(
                "CAP API: Попытка создания корректировки баланса с нарушением бизнес-правил - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.createBalanceAdjustment(
                                registeredPlayer.walletData().playerUUID(),
                                utils.getAuthorizationHeader(),
                                platformNodeId,
                                UUID.randomUUID().toString(),
                                request
                        ),
                        "cap_api.create_balance_adjustment.expected_exception"
                )
        );

        assertValidationError(error, 400, expectedMessage, expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestParameterValidationScenarios")
    @DisplayName("Создание корректировки баланса: ошибки в параметрах запроса")
    void shouldReturnClientErrorWhenRequestParametersInvalid(
            String description,
            Function<RegisteredPlayerData, String> playerUuidProvider,
            Consumer<CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder> requestCustomizer,
            int expectedStatus,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors
    ) {
        var requestBuilder = baseRequestBuilder();
        requestCustomizer.accept(requestBuilder);
        var request = requestBuilder.build();

        var error = step(
                "CAP API: Попытка создания корректировки баланса с некорректными параметрами запроса - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.createBalanceAdjustment(
                                playerUuidProvider.apply(registeredPlayer),
                                utils.getAuthorizationHeader(),
                                platformNodeId,
                                UUID.randomUUID().toString(),
                                request
                        ),
                        "cap_api.create_balance_adjustment.expected_exception"
                )
        );

        assertValidationError(error, expectedStatus, expectedMessage, expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizationErrorScenarios")
    @DisplayName("Создание корректировки баланса: ошибки авторизации")
    void shouldReturnUnauthorizedWhenAuthorizationHeaderInvalid(
            String description,
            String authorizationHeader
    ) {
        var request = baseRequestBuilder().build();

        var error = step(
                "CAP API: Попытка создания корректировки баланса без корректного заголовка авторизации - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.createBalanceAdjustment(
                                registeredPlayer.walletData().playerUUID(),
                                authorizationHeader,
                                platformNodeId,
                                UUID.randomUUID().toString(),
                                request
                        ),
                        "cap_api.create_balance_adjustment.expected_exception"
                )
        );

        assertValidationError(error, 401, "Full authentication is required to access this resource.", Map.of());
    }

    private CreateBalanceAdjustmentRequest.CreateBalanceAdjustmentRequestBuilder baseRequestBuilder() {
        return CreateBalanceAdjustmentRequest.builder()
                .currency(registeredPlayer.walletData().currency())
                .amount(VALID_ADJUSTMENT_AMOUNT)
                .reason(ReasonType.MALFUNCTION)
                .operationType(OperationType.CORRECTION)
                .direction(DirectionType.INCREASE)
                .comment(REQUEST_COMMENT);
    }
}
