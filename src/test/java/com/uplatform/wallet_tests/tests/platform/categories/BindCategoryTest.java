package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGameCategory;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v1.BindGameCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v1.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v1.CreateCategoryResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v1.DeleteCategoryRequest;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.uplatform.wallet_tests.tests.util.comparator.PayloadComparatorStrategy.log;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.ALIAS;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.TITLE;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Этот тест проверяет API CAP добавление игр в категорию.
 *
 * <h3> Сценарий: Добавление игр в категорию.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание новой категории.</b>
 *  {@link CreateCategoryParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданной категории в БД.</b>
 *  {@link CreateCategoryParameterizedTest}</li>
 *      <li><b>Получение случайных UUID игр из БД.</b>
 *  Получение случайных uuid игр из БД {@code ..._core.game}</li>
 *      <li><b>Добавление игр в категорию:</b>
 *  Добавление игр в категорию по API CAP, по ручке {@code POST  /_cap/api/v1/categories/{uuid}/bind-game}.
 *  В ответе есть все обязательные поля</li>
 *      <li><b>Проверяем, что в БД у игр появилась категория.</b>
 *  Проверяем в таблице {@code ..._core.game_categories_games},
 *  что у игры появилась категория по uuid игры и uuid категории</li>
 *      <li><b>Проверяем сообщение в Кафке.</b></li>
 *      <li><b>Постусловие. Удаление созданной категории.</b> {@link DeleteCategoryTest}</li>
 * </ol>
 */

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}/bind-game")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory") @Tag("BindGameCategory")
@Execution(ExecutionMode.SAME_THREAD)
public class BindCategoryTest extends BaseTest {

    static final class TestContext {
        CreateCategoryRequest createCategoryRequest;
        ResponseEntity<CreateCategoryResponse> createGameCategoryResponse;

        DeleteCategoryRequest deleteCategoryRequest;
        ResponseEntity<Void> deleteGameCategoryResponse;

        CoreGameCategory category;

        BindGameCategoryRequest bindGameCategoryRequest;
        ResponseEntity<Void> bindGameCategoryResponse;

        public List<String> randomGameUuids;
    }

    private final BindCategoryTest.TestContext ctx = new BindCategoryTest.TestContext();

    @BeforeEach
    void setUp() {

        step("1. Предусловие. Cоздание категории", () -> {
            ctx.createCategoryRequest = CreateCategoryRequest
                    .builder()
                    .alias(get(ALIAS, 5))
                    .type(CategoryType.VERTICAL)
                    .sort(55)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 5)))
                    .build();

            ctx.createGameCategoryResponse = capAdminClient.createCategory(
                            utils.getAuthorizationHeader(),
                            configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                            ctx.createCategoryRequest
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
    @DisplayName("Привязка игр к категории")
    void shouldGetCategory() {

        step("3. Получение случайных UUID игр из БД", () -> {
            List<String> randomGameUuids = coreDatabaseClient.findRandomGameUuids(5);
            ctx.randomGameUuids = randomGameUuids;
            log.info("Выбраны случайные UUID: {}", randomGameUuids);
        });

        step("4. Добавление игры в категорию", () ->{
            ctx.bindGameCategoryRequest = BindGameCategoryRequest
                    .builder()
                    .gameIds(ctx.randomGameUuids)
                    .build();

            ctx.bindGameCategoryResponse = capAdminClient.bindGameCategory(
                    ctx.category.getUuid(),  // UUID категории, в которую добавляем игры
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.bindGameCategoryRequest
            );

            assertEquals(HttpStatus.NO_CONTENT, ctx.bindGameCategoryResponse.getStatusCode(),
                    "Ожидаем 204 No Content при добавлении игр в категорию");
        });

        step("5. DB Category: Проверка что у игры появилась категория в БД", () -> {

        });

        step("Kafka: platform отправляет сообщение о создании категории в Kafka, в топик _core.gambling.v3.Game", () -> {

        });
    }

    @AfterEach
    void tearDown() {

        step("7. Постусловие. Удаление категории по ID", () -> {
            ctx.deleteCategoryRequest = DeleteCategoryRequest
                    .builder()
                    .id(ctx.createGameCategoryResponse.getBody().getId())
                    .build();

            ctx.deleteGameCategoryResponse = capAdminClient.deleteCategory(
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
