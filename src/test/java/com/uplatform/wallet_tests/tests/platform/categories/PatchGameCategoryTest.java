package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.category.*;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Этот тест проверяет API CAP на успешность изменения игровой категории.
 *
 * <h3> Сценарий: Успешное изменение игровой категории.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание новой категории.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданной категории в БД.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Изменение данных категории:</b>
 *  Изменение названия, алиаса и типа категории по API CAP, по ручке {@code PATCH  /_cap/api/v1/categories/{uuid}}</li>
 *      <li><b>Постусловие: удаление созданной категории.</b> {@link DeleteGameCategoryTest}</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory")
public class PatchGameCategoryTest extends BaseTest {

    @Test
    @DisplayName("Изменение категории")
    void shouldPatchCategory() {

        final class TestContext {
            CreateGameCategoryRequest createGameCategoryRequest;
            ResponseEntity<CreateGameCategoryResponse> createGameCategoryResponse;
            PatchGameCategoryRequest patchGameCategoryRequest;
            ResponseEntity<PatchGameCategoryResponse> patchGameCategoryResponse;
            DeleteGameCategoryRequest deleteGameCategoryRequest;
            ResponseEntity<Void> deleteGameCategoryResponse;
            String createdGameCategoryId;
        }
        final TestContext ctx = new TestContext();

        step("1. Предусловие. Создание категории", () -> {
            ctx.createGameCategoryRequest = CreateGameCategoryRequest.builder()
                    .alias(get(ALIAS, 7))
                    .type(CategoryType.VERTICAL)
                    .sort(1)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 7)))
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

        step("3. Изменение категории", () -> {
            ctx.patchGameCategoryRequest = PatchGameCategoryRequest.builder()
                    .alias(get(ALIAS, 11))
                    .type(CategoryType.HORIZONTAL)
                    .sort(1)
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 11)))
                    .build();

            ctx.patchGameCategoryResponse = capAdminClient.patchGameCategory(
                    ctx.createdGameCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.patchGameCategoryRequest
            );

            assertAll(
                    "Проверяем код ответа и тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.patchGameCategoryResponse.getBody())
            );
        });

        step("4. Постусловие. Удаление бренда по ID", () -> {
            ctx.deleteGameCategoryRequest = DeleteGameCategoryRequest.builder().id(ctx.createdGameCategoryId).build();

            ctx.deleteGameCategoryResponse = capAdminClient.deleteGameCategory(
                    ctx.createdGameCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(
                    HttpStatus.NO_CONTENT, ctx.deleteGameCategoryResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });
    }
}
