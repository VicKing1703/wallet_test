package com.uplatform.wallet_tests.api.nats.dto.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NatsTransactionDirectionTest {

    @Test
    void fromValueSupportsNumericCodes() {
        assertEquals(NatsTransactionDirection.WITHDRAW, NatsTransactionDirection.fromValue("1"));
        assertEquals(NatsTransactionDirection.DEPOSIT, NatsTransactionDirection.fromValue("2"));
    }

    @Test
    void fromValueReturnsUnknownForInvalidValue() {
        assertEquals(NatsTransactionDirection.UNKNOWN, NatsTransactionDirection.fromValue("some"));
    }
}
