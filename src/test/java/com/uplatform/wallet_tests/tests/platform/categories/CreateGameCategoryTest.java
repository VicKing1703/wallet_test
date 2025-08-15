package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.api.http.cap.dto.category.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.category.CreateCategoryResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.category.DeleteCategoryRequest;
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

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("Category")
class CreateCategoryParameterizedTest extends BaseParameterizedTest {

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
    void shouldCreateCategory(CategoryType categoryType, Integer titleLengths, Integer aliasLengths) {

        final class TestContext {
            CreateCategoryRequest  createCategoryRequest;
            ResponseEntity<CreateCategoryResponse> createCategoryResponse;
            DeleteCategoryRequest deleteCategoryRequest;
            ResponseEntity<Void> deleteCategoryResponse;
            String createdCategoryId;
        }

        final TestContext ctx = new TestContext();

        // Временный костыль. Надо подумать, т.к. дедлок срабатывает и при создании новой категории
        step("Ожидание перед удалением (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунды
        });

        step("Создание новой категории", () -> {
            ctx.createCategoryRequest = CreateCategoryRequest.builder()
                    .alias(get(ALIAS, aliasLengths))
                    .type(categoryType)
                    .sort(1)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, titleLengths)))
                    .build();

            ctx.createCategoryResponse = capAdminClient.createCategory(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createCategoryRequest
            );

            assertAll(
              "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createCategoryResponse.getBody().getId())
            );

            ctx.createdCategoryId = ctx.createCategoryResponse.getBody().getId();

        });


        step("DB Category: проверка создания rfntujhbb", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(ctx.createdCategoryId);
            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(category.getUuid(), ctx.createdCategoryId)
            );
        });

        step("Постусловие: Удаление категории по ID", () -> {
            ctx.deleteCategoryRequest = DeleteCategoryRequest.builder().id(ctx.createdCategoryId).build();

            ctx.deleteCategoryResponse = capAdminClient.deleteCategory(
                    ctx.createdCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(HttpStatus.NO_CONTENT, ctx.deleteCategoryResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });
    }
}