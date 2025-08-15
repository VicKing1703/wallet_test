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

        // Временный костыль. Надо подумать, т.к. дедлок срабатывает и при создании новой категории
        step("Ожидание перед удалением (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунды
        });

        step("Создание категории", () -> {
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
                    "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createGameCategoryResponse.getBody().getId())
            );

            ctx.createdGameCategoryId = ctx.createGameCategoryResponse.getBody().getId();

        });


        step("DB Category: проверка создания категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(ctx.createdGameCategoryId);
            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(category.getUuid(), ctx.createdGameCategoryId)
            );
        });

        step("Получение категории по ID", () -> {

            ctx.getGameCategoryIdRequest = GetGameCategoryRequest.builder().id(ctx.createdGameCategoryId).build();

            ctx.getGameCategoryResponse = capAdminClient.getGameCategoryId(
                    ctx.createdGameCategoryId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertAll(
                    "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getId()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getName()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getAlias()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getProjectId()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getGroupId()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getType()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getPassToCms()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getGamesCount()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getGameIds()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getStatus()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getSort()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getIsDefault()),
                    () -> assertNotNull(ctx.getGameCategoryResponse.getBody().getNames())
            );

            //временный костыль
            step("Ожидание перед удалением (для избежания deadlock)", () -> {
                Thread.sleep(2000); // пауза 2 секунды
            });

            step("Постусловие: Удаление категории по ID", () -> {

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