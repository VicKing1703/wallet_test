package com.uplatform.wallet_tests.tests.wallet.admin;

import com.uplatform.wallet_tests.tests.base.BaseNegativeParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий негативные сценарии для эндпоинта обновления блокировок игрока:
 * {@code PATCH /_cap/api/v1/players/{playerUUID}/blockers}.
 *
 * <p><b>Идея теста:</b> Убедиться, что API надежно защищено от некорректных данных и несанкционированного доступа.
 * Система должна отклонять любые запросы, которые не соответствуют контракту, и возвращать клиенту понятные,
 * структурированные ошибки. Ключевая проверка заключается в том, что ни один из невалидных запросов
 * не должен приводить к изменению состояния блокировок игрока.</p>
 *
 * <p><b>Сценарии тестирования сгруппированы по типу ошибки:</b></p>
 * <ol>
 *   <li><b>Ошибки валидации тела запроса (HTTP 400):</b>
 *     <ul>
 *       <li>Отправка запроса с отсутствующими (null) обязательными полями {@code gamblingEnabled} и {@code bettingEnabled}.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ошибки в параметре пути {@code playerUUID} (HTTP 400, 404):</b>
 *     <ul>
 *       <li>Отправка запроса с несуществующим, но валидным UUID игрока.</li>
 *       <li>Отправка запроса с невалидным форматом UUID.</li>
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
 *   <li>Тело ответа содержит правильное сообщение об ошибке и, при необходимости, детализированную информацию в поле {@code errors}.</li>
 *   <li>Состояние игрока в базе данных и Redis остается неизменным.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Управление игроком")
@Suite("Ручные блокировки гемблинга и беттинга: Негативные сценарии")
@Tag("Wallet") @Tag("CAP")
class BlockersNegativeParametrizedTest extends BaseNegativeParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private String platformNodeId;

    @BeforeAll
    void setup() {
        this.platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
    }

    @BeforeEach
    void registerPlayer() {
        step("Default Step: Регистрация нового пользователя", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });
    }

    static Stream<Arguments> requestBodyValidationProvider() {
        return Stream.of(
                arguments(
                        "Параметр тела gamblingEnabled: отсутствует",
                        UpdateBlockersRequest.builder()
                                .gamblingEnabled(null)
                                .bettingEnabled(true)
                                .build(),
                        Map.of("gamblingEnabled", List.of("value.not.null"))
                ),
                arguments(
                        "Параметр тела bettingEnabled: отсутствует",
                        UpdateBlockersRequest.builder()
                                .gamblingEnabled(true)
                                .bettingEnabled(null)
                                .build(),
                        Map.of("bettingEnabled", List.of("value.not.null"))
                )
        );
    }

    static Stream<Arguments> playerUuidValidationProvider() {
        return Stream.of(
                arguments(
                        "Параметр пути playerUuid: рандомный UUID",
                        UUID.randomUUID().toString(),
                        HttpStatus.NOT_FOUND,
                        "field:[wallet] msg:[not found]",
                        Map.of()
                ),
                arguments(
                        "Параметр пути playerUuid: невалидный формат UUID",
                        "not-a-uuid",
                        HttpStatus.BAD_REQUEST,
                        "Validation message",
                        Map.of()
                )
        );
    }

    static Stream<Arguments> authorizationValidationProvider() {
        return Stream.of(
                arguments(
                        "Заголовок Authorization: отсутствие заголовка",
                        null
                ),
                arguments(
                        "Заголовок Authorization: пустая строка",
                        ""
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestBodyValidationProvider")
    @DisplayName("Ручные блокировки гемблинга и беттинга: валидация тела запроса")
    void shouldReturnBadRequestWhenRequestBodyInvalid(
            String description,
            UpdateBlockersRequest invalidRequest,
            Map<String, List<String>> expectedFieldErrors
    ) {
        var error = step(
                "CAP API: Попытка обновления блокировок с некорректным телом - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.updateBlockers(
                                registeredPlayer.walletData().playerUUID(),
                                utils.getAuthorizationHeader(),
                                platformNodeId,
                                invalidRequest
                        ),
                        "cap_api.update_blockers.expected_exception"
                )
        );

        assertValidationError(error, HttpStatus.BAD_REQUEST.value(), "Validation error", expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("playerUuidValidationProvider")
    @DisplayName("Ручные блокировки гемблинга и беттинга: валидация параметра пути")
    void shouldReturnErrorWhenPlayerUuidInvalid(
            String description,
            String invalidPlayerUuid,
            HttpStatus expectedStatus,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors
    ) {
        var request = UpdateBlockersRequest.builder()
                .gamblingEnabled(true)
                .bettingEnabled(true)
                .build();

        var error = step(
                "CAP API: Попытка обновления блокировок с некорректным playerUUID - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.updateBlockers(
                                invalidPlayerUuid,
                                utils.getAuthorizationHeader(),
                                platformNodeId,
                                request
                        ),
                        "cap_api.update_blockers.expected_exception"
                )
        );

        assertValidationError(error, expectedStatus.value(), expectedMessage, expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizationValidationProvider")
    @DisplayName("Ручные блокировки гемблинга и беттинга: ошибки авторизации")
    void shouldReturnUnauthorizedWhenAuthorizationHeaderInvalid(
            String description,
            String invalidAuthorizationHeader
    ) {
        var request = UpdateBlockersRequest.builder()
                .gamblingEnabled(true)
                .bettingEnabled(true)
                .build();

        var error = step(
                "CAP API: Попытка обновления блокировок с некорректным заголовком авторизации - " + description,
                () -> executeExpectingError(
                        () -> capAdminClient.updateBlockers(
                                registeredPlayer.walletData().playerUUID(),
                                invalidAuthorizationHeader,
                                platformNodeId,
                                request
                        ),
                        "cap_api.update_blockers.expected_exception"
                )
        );

        assertValidationError(error, HttpStatus.UNAUTHORIZED.value(), "Full authentication is required to access this resource.", Map.of());
    }
}