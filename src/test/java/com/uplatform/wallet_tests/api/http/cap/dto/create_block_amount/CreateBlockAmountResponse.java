package com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount;

import java.math.BigDecimal;

public record CreateBlockAmountResponse(
        String transactionId,
        String currency,
        BigDecimal amount,
        String reason,
        String userId,
        String userName,
        long createdAt
) {
}