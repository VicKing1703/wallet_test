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

        // Временный костыль. Надо подумать, т.к. дедлок срабатывает и при создании новой категории
        step("Ожидание перед удалением (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунды
        });

        step("Создание категории", () -> {
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
                    "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createGameCategoryResponse.getBody().getId())
            );

            ctx.createdGameCategoryId = ctx.createGameCategoryResponse.getBody().getId();

        });


        step("DB Category: проверка создания rfntujhbb", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(ctx.createdGameCategoryId);
            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(category.getUuid(), ctx.createdGameCategoryId)
            );
        });

        step("Изменение категории", () -> {
            ctx.patchGameCategoryRequest = PatchGameCategoryRequest.builder()
                    .alias(get(ALIAS, 11))
                    .type(CategoryType.VERTICAL)
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
                    "Проверяем тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.patchGameCategoryResponse.getBody())
            );
        });

        //временный костыль
        step("Ожидание (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунды
        });

        step("Постусловие: Удаление бренда по ID", () -> {
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
