package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.*;
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

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("Category")
public class PatchCategoryTest extends BaseTest {

    @Test
    @DisplayName("Изменение категории")
    void shouldPatchCategory() {

        final class TestContext {
            CreateCategoryRequest createCategoryRequest;
            ResponseEntity<CreateCategoryResponse> createCategoryResponse;
            PatchCategoryRequest patchCategoryRequest;
            ResponseEntity<PatchCategoryResponse> patchCategoryResponse;
            DeleteCategoryRequest deleteCategoryRequest;
            ResponseEntity<Void> DeleteCategoryResponse;
            String createdCategoryId;
        }
        final TestContext ctx = new TestContext();

        // Временный костыль. Надо подумать, т.к. дедлок срабатывает и при создании новой категории
        step("Ожидание перед удалением (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунды
        });

        step("Создание категории", () -> {
            ctx.createCategoryRequest = CreateCategoryRequest.builder()
                    .alias(get(ALIAS, 7))
                    .type(CategoryType.VERTICAL)
                    .sort(1)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 7)))
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

        step("Изменение категории", () -> {
            ctx.patchCategoryRequest = PatchCategoryRequest.builder()
                    .alias(get(ALIAS, 11))
                    .type(CategoryType.VERTICAL)
                    .sort(1)
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 11)))
                    .build();

            ctx.patchCategoryResponse = capAdminClient.patchCategory(
                    ctx.createdCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.patchCategoryRequest
            );

            assertAll(
                    "Проверяем тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.patchCategoryResponse.getBody())
            );
        });

        //временный костыль
        step("Ожидание (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунды
        });

        step("Постусловие: Удаление бренда по ID", () -> {
            ctx.deleteCategoryRequest = DeleteCategoryRequest.builder().id(ctx.createdCategoryId).build();

            ctx.DeleteCategoryResponse = capAdminClient.deleteCategory(
                    ctx.createdCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(
                    HttpStatus.NO_CONTENT, ctx.DeleteCategoryResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });
    }
}
