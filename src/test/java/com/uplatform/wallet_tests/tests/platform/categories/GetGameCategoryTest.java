package com.uplatform.wallet_tests.tests.platform.categories;

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
 * Этот тест проверяет API CAP на получение данных игровой категории.
 *
 * <h3> Сценарий: Успешное получение информации о игровой категории.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание новой категории.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданной категории в БД.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Получение информации по категории:</b>
 *  Получение информации о категории по API CAP, по ручке {@code GET  /_cap/api/v1/categories/{uuid}}.
 *  В ответе есть все обязательные поля</li>
 *      <li><b>Постусловие. Удаление созданной категории.</b> {@link DeleteGameCategoryTest}</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory")
class GetGameCategoryTest extends BaseTest {

    @Test
    @DisplayName("Получение категории по её ID")
    void shouldGetCategory() {

        final class TestContext {
            CreateGameCategoryRequest createGameCategoryRequest;
            ResponseEntity<CreateGameCategoryResponse> createGameCategoryResponse;
            GetGameCategoryRequest getGameCategoryIdRequest;
            ResponseEntity<GetGameCategoryResponse> getGameCategoryResponse;
            String createdGameCategoryId;
            DeleteGameCategoryRequest deleteGameCategoryRequest;
            ResponseEntity<Void> deleteGameCategoryResponse;
        }

        final TestContext ctx = new TestContext();

        step("1. Предусловие. Cоздание категории", () -> {
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

        step("3. Получение категории по ID", () -> {

            ctx.getGameCategoryIdRequest = GetGameCategoryRequest.builder().id(ctx.createdGameCategoryId).build();

            ctx.getGameCategoryResponse = capAdminClient.getGameCategoryId(
                    ctx.createdGameCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertAll(
                    "Проверяем код ответа тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode(),
                            "Ожидаем статус 200 ОК"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getId(),
                            "Ожидаем uuid в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getName(),
                            "Ожидаем имя (name) в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getAlias(),
                            "Ожидаем аливас (alias) в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getProjectId(),
                            "Ожидаем uuid проекта (projectId) в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getGroupId(),
                            "Ожидаем uuid группы проектов (groupId) в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getType(),
                            "Ожидаем type в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getPassToCms(),
                            "Ожидаем getPassToCms в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getGamesCount(),
                            "Ожидаем количество игр (gamesCount) в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getGameIds(),
                            "Ожидаем список игр (gamesIds) в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getStatus(),
                            "Ожидаем status в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getSort(),
                            "Ожидаем Sort в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getIsDefault(),
                            "Ожидаем IsDefault в теле"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getNames(),
                            "Ожидаем словарь имён в транслите (names) в теле")
            );

            step("4. Постусловие. Удаление категории по ID", () -> {

                ctx.deleteGameCategoryRequest = DeleteGameCategoryRequest.builder().id(ctx.createdGameCategoryId).build();

                ctx.deleteGameCategoryResponse = capAdminClient.deleteGameCategory(
                        ctx.createdGameCategoryId,
                        utils.getAuthorizationHeader(),
                        configProvider.getEnvironmentConfig().getPlatform().getNodeId()
                );

                assertEquals(HttpStatus.NO_CONTENT, ctx.deleteGameCategoryResponse.getStatusCode(),
                        "Ожидаем статус 204 No Content"
                );
            });
        });
    }
}