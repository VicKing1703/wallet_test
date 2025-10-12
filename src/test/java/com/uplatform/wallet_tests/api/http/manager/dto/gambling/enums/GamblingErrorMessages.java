package com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums;

/**
 * Константы сообщений об ошибках, возвращаемых Gambling API.
 * Используются для проверки корректности ответов в негативных сценариях тестирования.
 */
public final class GamblingErrorMessages {

    private GamblingErrorMessages() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Ошибки токена сессии
    public static final String MISSING_SESSION_TOKEN = "missing session token";

    // Ошибки валидации amount
    public static final String AMOUNT_NEGATIVE = "validate request: amount: value [-1] must be greater or equal than [0].";
    public static final String AMOUNT_NEGATIVE_NO_LESS_THAN_ZERO = "validate request: amount: must be no less than 0.";

    // Ошибки бизнес-логики
    public static final String INSUFFICIENT_BALANCE = "insufficient balance";

    // Ошибки валидации transactionId
    public static final String TRANSACTION_ID_BLANK = "validate request: transactionId: cannot be blank.";
    public static final String TRANSACTION_ID_INVALID_UUID = "validate request: transactionId: must be a valid UUID.";

    // Ошибки валидации type
    public static final String TYPE_BLANK = "validate request: type: cannot be blank.";
    public static final String TYPE_INVALID = "validate request: type: must be a valid value.";

    // Ошибки валидации roundId
    public static final String ROUND_ID_BLANK = "validate request: roundId: cannot be blank.";
    public static final String ROUND_ID_TOO_LONG = "validate request: roundId: the length must be no more than 255.";

    // Ошибки валидации betTransactionId
    public static final String BET_TRANSACTION_ID_BLANK = "validate request: betTransactionId: cannot be blank.";
    public static final String BET_TRANSACTION_ID_INVALID_UUID = "validate request: betTransactionId: must be a valid UUID.";
}
