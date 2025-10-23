package com.uplatform.wallet_tests.tests.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.fasterxml.jackson.core.type.TypeReference;
import com.uplatform.wallet_tests.api.http.cap.dto.LocalizedName;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryResponse;
import com.uplatform.wallet_tests.api.db.entity.core.GameCategory;
import com.uplatform.wallet_tests.api.kafka.dto.GameCategoryMessage;
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

import java.util.Map;

import static com.uplatform.wallet_tests.api.db.entity.core.enums.GameCategoryStatus.DISABLED;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.ALIAS;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.NAME;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            Map<String, String> expectedLocalizedNames;
            GameCategoryMessage gameCategoryMessage;
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

            ctx.expectedLocalizedNames = objectMapper.convertValue(
                    ctx.request.getNames(),
                    new TypeReference<Map<String, String>>() {}
            );

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
                    () -> assertTrue(ctx.gameCategory.getCreatedAt() > 0, "core_db.game_category.created_at"),
                    () -> assertTrue(ctx.gameCategory.getUpdatedAt() >= ctx.gameCategory.getCreatedAt(), "core_db.game_category.updated_at"),
                    () -> assertEquals(PROJECT_ID, ctx.gameCategory.getProjectUuid(), "core_db.game_category.project_uuid"),
                    () -> assertTrue(ctx.gameCategory.getProjectGroupUuid().isBlank(),
                            "core_db.game_category.project_group_uuid"),
                    () -> assertEquals(DISABLED.statusId, ctx.gameCategory.getStatusId(), "core_db.game_category.status_id"),
                    () -> assertEquals(SORT_ORDER, ctx.gameCategory.getEntitySort(), "core_db.game_category.entity_sort"),
                    () -> assertFalse(ctx.gameCategory.isDefault(), "core_db.game_category.is_default"),
                    () -> assertNull(ctx.gameCategory.getParentUuid(), "core_db.game_category.parent_uuid"),
                    () -> assertEquals(ctx.request.getType().value(), ctx.gameCategory.getEntityType(), "core_db.game_category.entity_type"),
                    () -> assertEquals(ctx.expectedLocalizedNames, ctx.gameCategory.getLocalizedNames(), "core_db.game_category.localized_names"),
                    () -> assertFalse(ctx.gameCategory.isCms(), "core_db.game_category.cms")
            );
        });

        step("Kafka: Категория опубликована в топике core.gambling.v3.Game", () -> {
            ctx.gameCategoryMessage = kafkaClient.expect(GameCategoryMessage.class)
                    .with("category.uuid", ctx.responseBody.id())
                    .unique()
                    .fetch();

            var kafkaCategory = ctx.gameCategoryMessage.category();
            assertNotNull(kafkaCategory, "kafka.core_gambling_game.category");

            assertAll(
                    () -> assertNotNull(ctx.gameCategoryMessage.message(), "kafka.core_gambling_game.message"),
                    () -> assertEquals(ctx.request.getType().value(), kafkaCategory.type(), "kafka.core_gambling_game.category.type"),
                    () -> assertEquals(ctx.responseBody.id(), kafkaCategory.uuid(), "kafka.core_gambling_game.category.uuid"),
                    () -> assertEquals(ctx.request.getNames().getRu(), kafkaCategory.name(), "kafka.core_gambling_game.category.name"),
                    () -> assertEquals(ctx.expectedLocalizedNames, kafkaCategory.localizedNames(), "kafka.core_gambling_game.category.localized_names"),
                    () -> assertEquals(DISABLED.status, kafkaCategory.status(), "kafka.core_gambling_game.category.status"),
                    () -> assertEquals(ctx.request.getType().value(), ctx.gameCategoryMessage.message().eventType(), "kafka.core_gambling_game.message.event_type")
            );
        });
    }
}
