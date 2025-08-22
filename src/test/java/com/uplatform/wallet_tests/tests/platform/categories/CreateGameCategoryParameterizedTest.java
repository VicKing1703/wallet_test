package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.api.http.cap.dto.category.CreateGameCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.category.CreateGameCategoryResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.category.DeleteGameCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.ALIAS;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.TITLE;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Этот параметризированный тест проверяет API CAP на успешность создания игровых категорий типов:
 * вертикальная, горизонтальная и навигационная панель.
 *
 * <h3> Сценарий: Успешное создание игровой категории.</h3>
 * <ol>
 *      <li><b>Создание новой категории:</b>
 *  Создание новой категории через CAP, по ручке {@code POST /_cap/api/v1/categories}.
 *  В ответе код 200 и уникеальный uuid созданной категории</li>
 *      <li><b>Нахождение созданной категории в БД:</b>
 *  В БД {@code `_core`.game_category} есть запись с uuid из ответа на создание категории</li>
 *      <li><b>Постусловие: удаление созданной категории.</b> {@link DeleteGameCategoryTest}</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory")
class CreateGameCategoryParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> categoryParamsProvider() {
        return Stream.of(
                Arguments.of(
                        CategoryType.VERTICAL, 25, 2,
                        "Тип категории: вертикальная, длинны строк: названия - 25, алиас - 2"
                ),

                Arguments.of(
                        CategoryType.HORIZONTAL, 2, 100,
                        "Тип категории: горизонтальная, длинны строк: названия - 2, алиас - 100"
                ),
                Arguments.of(CategoryType.NAVIGATION_PANEL, 13, 33,
                        "Тип категории: нав. панель, длинны строк: названия - 13, алиас - 33")
        );
    }

    @ParameterizedTest(name = "Создание категории: название = {0}, длина названия = {1}, алиаса = {2}")
    @MethodSource("categoryParamsProvider")
    @DisplayName("Создание категории")
    void shouldCreateGameCategory(CategoryType categoryType, Integer titleLengths, Integer aliasLengths) {

        final class TestContext {
            CreateGameCategoryRequest createGameCategoryRequest;
            ResponseEntity<CreateGameCategoryResponse> createGameCategoryResponse;
            DeleteGameCategoryRequest deleteGameCategoryRequest;
            ResponseEntity<Void> deleteGameCategoryResponse;
            String createdGameCategoryId;
        }

        final TestContext ctx = new TestContext();

        step("1. Создание новой категории", () -> {
            ctx.createGameCategoryRequest = CreateGameCategoryRequest.builder()
                    .alias(get(ALIAS, aliasLengths))
                    .type(categoryType)
                    .sort(1)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, titleLengths)))
                    .build();

            ctx.createGameCategoryResponse = capAdminClient.createGameCategory(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createGameCategoryRequest
            );

            assertAll(
              "Проверка ответа создания категории",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createGameCategoryResponse.getBody().getId())
            );

            ctx.createdGameCategoryId = ctx.createGameCategoryResponse.getBody().getId();

        });


        step("2. DB Category: проверка создания категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(ctx.createdGameCategoryId);
            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(category.getUuid(), ctx.createdGameCategoryId,
                            "Uuid из ответа и в БД должны быть одинаковые")
            );
        });

        step("3. Постусловие. Удаление категории по uuid", () -> {
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
    }
}