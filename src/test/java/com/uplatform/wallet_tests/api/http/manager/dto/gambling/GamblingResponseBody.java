package com.uplatform.wallet_tests.api.http.manager.dto.gambling;

import java.math.BigDecimal;

/**
 * Response payload returned by Manager gambling endpoints (bet/win/refund/rollback/tournament).
 *
 * @param balance        Wallet balance after the processed operation.
 * @param transactionId  Idempotency UUID echoed by the Manager API.
 */
public record GamblingResponseBody(BigDecimal balance, String transactionId) {
}
