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
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform")
public class DeleteCategoryTest extends BaseTest{

    private String createdCategoryId;

    @Test
    @DisplayName("Удаление категории по её ID")
    void shouldCreateCategory() {

        final class TestContext {
            CreateCategoryRequest createCategoryRequest;
            ResponseEntity<CreateCategoryResponse> createCategoryResponse;
            DeleteCategoryRequest deleteCategoryRequest;
            ResponseEntity<Void> deleteCategoryResponse;
        }
        final TestContext ctx = new TestContext();

        step("Создание категории", () -> {
            ctx.createCategoryRequest = CreateCategoryRequest.builder()
                    .alias(get(ALIAS))
                    .type(CategoryType.VERTICAL)
                    .sort(2)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(CATEGORY_TITLE, 17)))
                    .build();

            ctx.createCategoryResponse = capAdminClient.createCategory(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getGroupId(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    configProvider.getEnvironmentConfig().getApi().getCapCredentials().getUsername(),
                    ctx.createCategoryRequest);

            assertAll(
                    "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createCategoryResponse.getBody().getId())
            );
            createdCategoryId = ctx.createCategoryResponse.getBody().getId();
        });

        step("Создана категория с ID: " + createdCategoryId);

        step("Ожидание перед удалением (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунда
        });

        step("Удаление категории по ID", () -> {
            ctx.deleteCategoryRequest = DeleteCategoryRequest.builder().id(createdCategoryId).build();

            ctx.deleteCategoryResponse = capAdminClient.deleteCategory(
                    createdCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getGroupId(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    configProvider.getEnvironmentConfig().getApi().getCapCredentials().getUsername());

            assertEquals(HttpStatus.NO_CONTENT, ctx.deleteCategoryResponse.getStatusCode(), "Ожидаем статус 204 No Content");
        });
    }
}
