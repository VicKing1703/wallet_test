package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.api.db.entity.core.CoreGameCategory;
import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.*;
import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.tests.util.utils.RetryUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
@Tag("Platform") @Tag("GameCategory") @Tag("GetGameCategory")
@Execution(ExecutionMode.SAME_THREAD)
class GetGameCategoryTest extends BaseTest {

    static final class TestContext {
        CreateGameCategoryRequest createGameCategoryRequest;
        ResponseEntity<CreateGameCategoryResponse> createGameCategoryResponse;

        GetGameCategoryRequest getGameCategoryIdRequest;
        ResponseEntity<GetGameCategoryResponse> getGameCategoryResponse;

        DeleteGameCategoryRequest deleteGameCategoryRequest;
        ResponseEntity<Void> deleteGameCategoryResponse;

        CoreGameCategory category;
    }

    private final TestContext ctx = new TestContext();

    @BeforeEach
    void setUp() {

        step("1. Предусловие. Cоздание категории", () -> {
            ctx.createGameCategoryRequest = CreateGameCategoryRequest
                    .builder()
                    .alias(get(ALIAS, 5))
                    .type(CategoryType.VERTICAL)
                    .sort(88)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 5)))
                    .build();

            ctx.createGameCategoryResponse = capAdminClient.createGameCategory(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createGameCategoryRequest
            );

            assertAll("Проверяем код ответа и тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode(),
                            "Код ответа должен быть 200 ОК, пришел" + ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createGameCategoryResponse.getBody().getId(),
                            "В ответе должен быть uuid созданной категории")
            );
        });


        step("2. Предусловие. DB Category: проверка создания категории", () -> {
            ctx.category = coreDatabaseClient
                    .findCategoryByUuidOrFail(ctx.createGameCategoryResponse.getBody().getId());

            assertAll("Проверка записи в БД",
                    () -> assertEquals(ctx.category.getUuid(), ctx.createGameCategoryResponse.getBody().getId(),
                            "Uuid из ответа и в БД должны быть одинаковые")
            );
        });
    }

    @Test
    @DisplayName("Получение категории по её ID")
    void shouldGetCategory() {

        step("3. Получение категории по ID", () -> {
            ctx.getGameCategoryIdRequest = GetGameCategoryRequest
                    .builder()
                    .id(ctx.createGameCategoryResponse.getBody().getId())
                    .build();

            ctx.getGameCategoryResponse = capAdminClient.getGameCategoryId(
                    ctx.createGameCategoryResponse.getBody().getId(),
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertAll("Проверяем код и тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode(),
                            "Ожидаем статус 200 ОК"),
                    () -> assertEquals(ctx.createGameCategoryResponse.getBody().getId(),
                            ctx.getGameCategoryResponse.getBody().getId(),
                            "uuid при создании и из получения должны быть одинаковые"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getName(),
                            "Ожидаем имя (name) в теле"),
                    () -> assertEquals(ctx.createGameCategoryRequest.getAlias(),
                            ctx.getGameCategoryResponse.getBody().getAlias(),
                            "alias при создании и из получения должны быть одинаковые"),
                    () -> assertEquals(ctx.createGameCategoryRequest.getProjectId(),
                            ctx.getGameCategoryResponse.getBody().getProjectId(),
                            "Ожидаем uuid проекта (projectId) в теле такой-же как при создании"),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getGroupId(),
                            "Ожидаем пустое значение группы проектов (groupId)"),
                    () -> assertEquals(ctx.createGameCategoryRequest.getType().getValue(),
                            ctx.getGameCategoryResponse.getBody().getType(),
                            "type при создании и из получения должны быть одинаковые"),
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
                    () -> assertEquals(ctx.createGameCategoryRequest.getNames().get(LangEnum.RUSSIAN),
                            ctx.getGameCategoryResponse.getBody().getNames().get("ru"),
                            "names при создании и из получения должны быть одинаковые")
            );
        });
    }

    @AfterEach
    void tearDown() {

        step("4. Постусловие. Удаление категории по ID", () -> {
            ctx.deleteGameCategoryRequest = DeleteGameCategoryRequest
                    .builder()
                    .id(ctx.createGameCategoryResponse.getBody().getId())
                    .build();

            ctx.deleteGameCategoryResponse = capAdminClient.deleteGameCategory(
                    ctx.createGameCategoryResponse.getBody().getId(),
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(HttpStatus.NO_CONTENT, ctx.deleteGameCategoryResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });
    }
}
