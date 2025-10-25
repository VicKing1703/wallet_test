package com.uplatform.wallet_tests.api.http.cap.dto.get_block_amount_list;

import java.math.BigDecimal;

public record BlockAmountListItem(
        String transactionId,
        String currency,
        BigDecimal amount,
        String reason,
        String userId,
        String userName,
        Long createdAt,
        String walletId,
        String playerId
) {
}
