package com.uplatform.wallet_tests.tests.base;

import com.uplatform.wallet_tests.api.http.cap.dto.errors.ValidationErrorResponse;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import feign.FeignException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Базовый класс для параметризованных негативных тестов.
 * Предоставляет общую логику для работы с ошибками API.
 */
public abstract class BaseNegativeParameterizedTest extends BaseParameterizedTest {

    /**
     * Выполняет API-запрос, ожидая FeignException, и парсит ответ в ValidationErrorResponse.
     *
     * @param requestExecutor supplier, выполняющий API-вызов через Feign-клиент
     * @param assertionMessage сообщение для assertion в случае отсутствия исключения
     * @return ValidationErrorResponse с деталями ошибки
     */
    protected ValidationErrorResponse executeExpectingError(
            Supplier<?> requestExecutor,
            String assertionMessage
    ) {
        return executeExpectingError(
                requestExecutor,
                assertionMessage,
                ValidationErrorResponse.class
        );
    }

    /**
     * Выполняет API-запрос, ожидая FeignException, и парсит ответ в указанный класс ошибки.
     *
     * @param requestExecutor supplier, выполняющий API-вызов через Feign-клиент
     * @param assertionMessage сообщение для assertion в случае отсутствия исключения
     * @param errorClass класс, в который необходимо распарсить тело ошибки
     * @return объект ошибки указанного типа
     */
    protected <T> T executeExpectingError(
            Supplier<?> requestExecutor,
            String assertionMessage,
            Class<T> errorClass
    ) {
        var exception = assertThrows(
                FeignException.class,
                requestExecutor::get,
                assertionMessage
        );

        return utils.parseFeignExceptionContent(exception, errorClass);
    }

    /**
     * Проверяет структуру ValidationErrorResponse.
     *
     * @param error объект ошибки для проверки
     * @param expectedCode ожидаемый HTTP-код
     * @param expectedMessage ожидаемое сообщение об ошибке
     * @param expectedFieldErrors ожидаемые ошибки полей
     */
    protected void assertValidationError(
            ValidationErrorResponse error,
            int expectedCode,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors
    ) {
        assertAll("cap_api.error.validation_structure",
                () -> assertEquals(expectedCode, error.code(), "cap_api.error.code"),
                () -> assertEquals(expectedMessage, error.message(), "cap_api.error.message"),
                () -> assertEquals(expectedFieldErrors, error.errors(), "cap_api.error.errors")
        );
    }

    /**
     * Проверяет структуру ошибки Manager API, использующей DTO {@link GamblingError}.
     *
     * @param error объект ошибки для проверки
     * @param expectedCode ожидаемый код ошибки
     * @param expectedMessage ожидаемое сообщение об ошибке
     */
    protected void assertValidationError(
            GamblingError error,
            int expectedCode,
            String expectedMessage
    ) {
        assertAll("manager_api.error.validation_structure",
                () -> assertEquals(expectedCode, error.code(), "manager_api.error.code"),
                () -> assertEquals(expectedMessage, error.message(), "manager_api.error.message")
        );
    }
}
