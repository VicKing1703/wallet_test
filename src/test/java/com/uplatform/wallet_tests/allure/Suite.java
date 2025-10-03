package com.uplatform.wallet_tests.allure;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import java.lang.annotation.*;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Suite {
    String value();
}