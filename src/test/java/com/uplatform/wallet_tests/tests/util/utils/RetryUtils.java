package com.uplatform.wallet_tests.tests.util.utils;

import feign.FeignException;
import java.util.function.Supplier;

public class RetryUtils {

    private static final int MAX_ATTEMPTS = 3;
    private static final long SLEEP_MILLIS = 200L;

    private RetryUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T> T withDeadlockRetry(Supplier<T> action) {
        int attempts = 0;
        while (true) {
            try {
                return action.get();
            } catch (FeignException e) {
                attempts++;
                if (e.status() == 500
                        && e.getMessage().contains("Deadlock found when trying to get lock")
                        && attempts < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(SLEEP_MILLIS);
                    } catch (InterruptedException ignored) {}
                    continue;
                }
                throw e;
            }
        }
    }
}
