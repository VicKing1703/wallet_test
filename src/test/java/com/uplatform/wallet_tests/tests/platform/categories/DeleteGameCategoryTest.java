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

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("Category")
public class DeleteCategoryTest extends BaseTest{

    @Test
    @DisplayName("Удаление категории по её ID")
    void shouldCreateCategory() {

        final class TestContext {
            CreateCategoryRequest createCategoryRequest;
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

        step("Создание категории", () -> {
            ctx.createCategoryRequest = CreateCategoryRequest.builder()
                    .alias(get(ALIAS, 5))
                    .type(CategoryType.VERTICAL)
                    .sort(1)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 5)))
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

        step("Удаление категории по ID", () -> {
            ctx.deleteCategoryRequest = DeleteCategoryRequest.builder()
                    .id(ctx.createdCategoryId)
                    .build();

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
