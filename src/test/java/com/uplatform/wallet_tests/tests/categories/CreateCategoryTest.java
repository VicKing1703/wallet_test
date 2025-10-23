package com.uplatform.wallet_tests.tests.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.LocalizedName;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryResponse;
import com.uplatform.wallet_tests.api.db.entity.core.GameCategory;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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
@Tag("CAP") @Tag("Platform")
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
            GameCategory gameCategory;
        }
        final TestContext ctx = new TestContext();

        step("CAP API: Создание категории игр", () -> {
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

        step("Core DB: Категория сохранена", () -> {
            ctx.gameCategory = coreDatabaseClient.findGameCategoryByUuidOrFail(ctx.responseBody.id());

            assertAll(
                    () -> assertEquals(ctx.responseBody.id(), ctx.gameCategory.getUuid(), "core_db.game_category.uuid"),
                    () -> assertEquals(ctx.request.getAlias(), ctx.gameCategory.getAlias(), "core_db.game_category.alias"),
                    () -> assertEquals(PROJECT_ID, ctx.gameCategory.getProjectUuid(), "core_db.game_category.project_uuid"),
                    () -> assertEquals(ctx.request.getType().value(), ctx.gameCategory.getEntityType(), "core_db.game_category.entity_type"),
                    () -> assertEquals(ctx.request.getNames().getRu(), ctx.gameCategory.getLocalizedNames().get("ru"), "core_db.game_category.localized_names.ru"),
                    () -> assertEquals(ctx.request.getNames().getEn(), ctx.gameCategory.getLocalizedNames().get("en"), "core_db.game_category.localized_names.en"),
                    () -> assertEquals(ctx.request.getNames().getLv(), ctx.gameCategory.getLocalizedNames().get("lv"), "core_db.game_category.localized_names.lv")
            );
        });
    }
}
