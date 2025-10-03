package com.uplatform.wallet_tests.tests.base;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import org.junit.jupiter.api.TestInstance;

/**
 * Base class for parameterized tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseParameterizedTest extends BaseTest {
}
