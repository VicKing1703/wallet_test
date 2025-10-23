package com.uplatform.platform.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.LocalizedName;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryResponse;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.ALIAS;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.NAME;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Категории")
@Suite("Создание категорий: Позитивные сценарии")
@Tag("CAP")
@Tag("Platform")
class CreateCategoryTest extends BaseTest {

    @Test
    @DisplayName("CAP: Создание категории - успешный ответ содержит идентификатор.")
    void shouldCreateCategory() {
        final String PROJECT_ID = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String PLATFORM_USER_ID = HttpServiceHelper.getCapPlatformUserId(configProvider.getEnvironmentConfig().getHttp());
        final String PLATFORM_USERNAME = HttpServiceHelper.getCapPlatformUsername(configProvider.getEnvironmentConfig().getHttp());
        final int SORT_ORDER = 1;

        final class TestContext {
            CreateCategoryRequest request;
            CreateCategoryResponse responseBody;
        }
        final TestContext ctx = new TestContext();

        step("CAP API: Подготовка и отправка запроса на создание категории", () -> {
            ctx.request = CreateCategoryRequest.builder()
                    .names(LocalizedName.builder()
                            .ru(get(NAME, 10))
                            .en(get(NAME, 10))
                            .lv(get(NAME, 10))
                            .build())
                    .alias(get(ALIAS, 12))
                    .sort(SORT_ORDER)
                    .projectId(PROJECT_ID)
                    .type(CategoryType.CATEGORY)
                    .build();

            var response = capAdminClient.createCategory(
                    utils.getAuthorizationHeader(),
                    PLATFORM_USER_ID,
                    PLATFORM_USERNAME,
                    ctx.request
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.categories.create.status_code");
            ctx.responseBody = response.getBody();
            assertNotNull(ctx.responseBody, "cap_api.categories.create.response_body");
            assertAll(
                    () -> assertNotNull(ctx.responseBody.id(), "cap_api.categories.create.id")
            );
        });
    }
}
