package com.uplatform.wallet_tests.tests.wallet.admin;

import com.uplatform.wallet_tests.tests.base.BaseNegativeParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountRequest;
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
 * Интеграционный тест, проверяющий негативные сценарии для эндпоинта создания ручной блокировки средств:
 * {@code POST /_cap/api/v1/wallet/{playerUuid}/create-block-amount}.
 *
 * <p><b>Идея теста:</b> Гарантировать, что система строго контролирует операции, влияющие на баланс игрока.
 * API должен отклонять любые запросы на блокировку средств, которые являются невалидными, нарушают бизнес-логику
 * (например, нехватка средств) или отправлены без должных прав. Это защищает систему от создания некорректных
 * финансовых блокировок и сохраняет целостность данных кошелька.</p>
 *
 * <p><b>Сценарии тестирования сгруппированы по типу ошибки:</b></p>
 * <ol>
 *   <li><b>Ошибки валидации тела запроса (HTTP 400):</b>
 *     <ul>
 *       <li>Отправка запроса с отсутствующими (null), пустыми или некорректными значениями для полей
 *           {@code amount} и {@code currency}.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ошибки бизнес-логики (HTTP 400):</b>
 *     <ul>
 *       <li>Попытка заблокировать сумму, превышающую текущий доступный баланс игрока.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ошибки в параметрах запроса (HTTP 404):</b>
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
 *   <li>Тело ответа содержит понятное сообщение об ошибке.</li>
 *   <li>Баланс игрока и список его блокировок остаются неизменными.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Управление игроком")
@Suite("Ручная блокировка баланса игрока: Негативные сценарии")
@Tag("Wallet") @Tag("CAP")
class BlockAmountNegativeParametrizedTest extends BaseNegativeParameterizedTest {

    private static final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal VALID_BLOCK_AMOUNT = new BigDecimal("50.00");
    private static final String REQUEST_REASON = "Test block amount negative";

    private RegisteredPlayerData registeredPlayer;
    private String platformNodeId;

    @BeforeEach
    void setUp() {
        platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(ADJUSTMENT_AMOUNT);
            assertNotNull(registeredPlayer, "default_step.registration");
        });
    }

    static Stream<Arguments> requestBodyValidationScenarios() {
        return Stream.of(
                arguments(
                        "Параметр тела amount: отсутствует",
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> builder.amount(null),
                        "Validation error",
                        Map.of("amount", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела amount: отрицательный",
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> builder.amount("-50.00"),
                        "Validation message",
                        Map.of()
                ),
                arguments(
                        "Параметр тела amount: пустая строка",
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> builder.amount(""),
                        "Validation error",
                        Map.of("amount", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела currency: отсутствует",
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> builder.currency(null),
                        "Validation error",
                        Map.of("currency", List.of("value.not.blank"))
                ),
                arguments(
                        "Параметр тела currency: пустая строка",
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> builder.currency(""),
                        "Validation error",
                        Map.of("currency", List.of("value.not.blank"))
                )
        );
    }

    static Stream<Arguments> businessRuleViolationScenarios() {
        return Stream.of(
                arguments(
                        "Параметр тела amount: превышает баланс",
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> builder.amount("1150.00"),
                        "field:[amount] msg:[wallet balance is not enough]",
                        Map.of()
                )
        );
    }

    static Stream<Arguments> requestParameterValidationScenarios() {
        return Stream.of(
                arguments(
                        "Параметр пути playerUuid: рандомный UUID",
                        (Function<RegisteredPlayerData, String>) playerData -> UUID.randomUUID().toString(),
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> {},
                        404,
                        "field:[wallet] msg:[not found]",
                        Map.of()
                ),
                arguments(
                        "Параметр пути playerUuid: невалидный формат UUID",
                        (Function<RegisteredPlayerData, String>) playerData -> "not-a-uuid",
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> {},
                        404,
                        "Not found",
                        Map.of()
                ),
                arguments(
                        "Параметр тела currency: не совпадает с валютой игрока",
                        (Function<RegisteredPlayerData, String>) playerData -> playerData.walletData().playerUUID(),
                        (Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder>) builder -> builder.currency("BTC"),
                        404,
                        "field:[wallet] msg:[not found]",
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
    @DisplayName("Создание блокировки: ошибки валидации тела запроса")
    void shouldReturnBadRequestWhenRequestBodyInvalid(
            String description,
            Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder> requestCustomizer,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors
    ) {
        var requestBuilder = baseRequestBuilder();
        requestCustomizer.accept(requestBuilder);
        var request = requestBuilder.build();

        var error = step(
                "CAP API: Попытка создания блокировки с некорректным телом запроса - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.createBlockAmount(
                                registeredPlayer.walletData().playerUUID(),
                                utils.getAuthorizationHeader(),
                                platformNodeId,
                                request
                        ),
                        "cap_api.create_block_amount.expected_exception"
                )
        );

        assertValidationError(error, 400, expectedMessage, expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("businessRuleViolationScenarios")
    @DisplayName("Создание блокировки: ошибки бизнес-логики")
    void shouldReturnBadRequestWhenBusinessRulesViolated(
            String description,
            Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder> requestCustomizer,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors
    ) {
        var requestBuilder = baseRequestBuilder();
        requestCustomizer.accept(requestBuilder);
        var request = requestBuilder.build();

        var error = step(
                "CAP API: Попытка создания блокировки с нарушением бизнес-правил - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.createBlockAmount(
                                registeredPlayer.walletData().playerUUID(),
                                utils.getAuthorizationHeader(),
                                platformNodeId,
                                request
                        ),
                        "cap_api.create_block_amount.expected_exception"
                )
        );

        assertValidationError(error, 400, expectedMessage, expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestParameterValidationScenarios")
    @DisplayName("Создание блокировки: ошибки в параметрах запроса")
    void shouldReturnClientErrorWhenRequestParametersInvalid(
            String description,
            Function<RegisteredPlayerData, String> playerUuidProvider,
            Consumer<CreateBlockAmountRequest.CreateBlockAmountRequestBuilder> requestCustomizer,
            int expectedStatus,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors
    ) {
        var requestBuilder = baseRequestBuilder();
        requestCustomizer.accept(requestBuilder);
        var request = requestBuilder.build();

        var error = step(
                "CAP API: Попытка создания блокировки с некорректными параметрами запроса - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.createBlockAmount(
                                playerUuidProvider.apply(registeredPlayer),
                                utils.getAuthorizationHeader(),
                                platformNodeId,
                                request
                        ),
                        "cap_api.create_block_amount.expected_exception"
                )
        );

        assertValidationError(error, expectedStatus, expectedMessage, expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizationErrorScenarios")
    @DisplayName("Создание блокировки: ошибки авторизации")
    void shouldReturnUnauthorizedWhenAuthorizationHeaderInvalid(
            String description,
            String authorizationHeader
    ) {
        var request = baseRequestBuilder().build();

        var error = step(
                "CAP API: Попытка создания блокировки без корректного заголовка авторизации - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.createBlockAmount(
                                registeredPlayer.walletData().playerUUID(),
                                authorizationHeader,
                                platformNodeId,
                                request
                        ),
                        "cap_api.create_block_amount.expected_exception"
                )
        );

        assertValidationError(error, 401, "Full authentication is required to access this resource.", Map.of());
    }

    private CreateBlockAmountRequest.CreateBlockAmountRequestBuilder baseRequestBuilder() {
        return CreateBlockAmountRequest.builder()
                .reason(REQUEST_REASON)
                .amount(VALID_BLOCK_AMOUNT.toString())
                .currency(registeredPlayer.walletData().currency());
    }
}