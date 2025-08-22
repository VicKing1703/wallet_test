package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.api.db.exceptions.DatabaseRecordNotFoundException;
import com.uplatform.wallet_tests.api.http.cap.dto.category.*;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.allure.Suite;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.ALIAS;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.TITLE;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Этот тест проверяет API CAP на успешность удаления игровой категорий.
 *
 * <h3> Сценарий: Успешное удаление игровой категории.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание новой категории.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданной категории в БД.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Удаление созданной категории.</b>
 *  Успешное удаление созданной категории по API CAP, по ручке {@code DELETE /_cap/api/v1/categories/{uuid}},
 *  по uuid из ответа на создание. В ответ получаем код 204</li>
 *      <li><b>Проверка удаления категории в БД.</b>
 *  Проверка, что в БД {@code `_core`.game_category} отсутствует категория с uuid из создания категории</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory") @Tag("DeleteGameCategory")
public class DeleteGameCategoryTest extends BaseTest{

    @Test
    @DisplayName("Удаление категории по её uuid")
    void shouldDeleteGameCategory() {

        final class TestContext {
            CreateGameCategoryRequest createGameCategoryRequest;
            ResponseEntity<CreateGameCategoryResponse> createGameCategoryResponse;
            DeleteGameCategoryRequest deleteGameCategoryRequest;
            ResponseEntity<Void> deleteGameCategoryResponse;
            String createdGameCategoryId;
        }
        final TestContext ctx = new TestContext();

        step("1. Предусловие. Создание категории", () -> {
            ctx.createGameCategoryRequest = CreateGameCategoryRequest.builder()
                    .alias(get(ALIAS, 5))
                    .type(CategoryType.VERTICAL)
                    .sort(1)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 5)))
                    .build();

            ctx.createGameCategoryResponse = capAdminClient.createGameCategory(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createGameCategoryRequest
            );

            assertAll(
                    "Проверяем код ответа и тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createGameCategoryResponse.getBody().getId())
            );

            ctx.createdGameCategoryId = ctx.createGameCategoryResponse.getBody().getId();

        });


        step("2. Предусловие. DB Category: проверка создания категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(ctx.createdGameCategoryId);
            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(category.getUuid(), ctx.createdGameCategoryId)
            );
        });

        step("3. Удаление категории по ID", () -> {
            ctx.deleteGameCategoryRequest = DeleteGameCategoryRequest.builder()
                    .id(ctx.createdGameCategoryId)
                    .build();

            ctx.deleteGameCategoryResponse = capAdminClient.deleteGameCategory(
                    ctx.createdGameCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(HttpStatus.NO_CONTENT, ctx.deleteGameCategoryResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });

        step("4. DB Category: проверка удаления категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuid(ctx.createdGameCategoryId);
            assertTrue(category.isEmpty(), "Категория с данным uuid должна быть удалена из БД");
        });
    }
}
