package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.*;
import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameCategoryEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.enums.GameEventType;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.tests.util.utils.RetryUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * Этот тест проверяет API CAP на успешность удаления игровой категорий.
 *
 * <h3> Сценарий: Успешное удаление игровой категории.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание новой категории.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданной категории в БД.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Удаление созданной категории.</b>
 *  Успешное удаление созданной категории по API CAP, по ручке {@code DELETE /_cap/api/v1/categories/{uuid}},
 *  по uuid из ответа на создание. В ответ получаем код 204</li>
 *      <li><b>Проверка удаления категории в БД.</b>
 *  Проверка, что в БД {@code `_core`.game_category} отсутствует категория с uuid из создания категории</li>
 *      <li><b>Поиск сообщения в Кафке о создании категории: </b>
 *  Проверяем в топике {@code core.gambling.v3.Game} сообщение об удалении категории, что его uuid соответствуют,
 *  тип категории, статус и имена</li>
 * </ol>
 */

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory") @Tag("DeleteGameCategory")
@Execution(ExecutionMode.SAME_THREAD)
public class DeleteGameCategoryTest extends BaseTest{

    static final class TestContext {
        CreateGameCategoryRequest createGameCategoryRequest;
        ResponseEntity<CreateGameCategoryResponse> createGameCategoryResponse;

        DeleteGameCategoryRequest deleteGameCategoryRequest;
        ResponseEntity<Void> deleteGameCategoryResponse;

        GameCategoryEvent gameCategoryEvent;
    }

    final TestContext ctx = new TestContext();

    @BeforeEach
    void setUp() {

        step("1. Предусловие. Создание категории", () -> {
            ctx.createGameCategoryRequest = CreateGameCategoryRequest.builder()
                    .alias(get(ALIAS, 5))
                    .type(CategoryType.VERTICAL)
                    .sort(77)
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
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createGameCategoryResponse.getBody().getId())
            );
        });

        step("2. Предусловие. DB Category: проверка создания категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(ctx.createGameCategoryResponse.getBody().getId());

            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(category.getUuid(), ctx.createGameCategoryResponse.getBody().getId())
            );
        });
    }

    @Test
    @DisplayName("Удаление категории по её uuid")
    void shouldDeleteGameCategory() {

        step("3. Удаление категории по ID", () -> {
            ctx.deleteGameCategoryRequest = DeleteGameCategoryRequest.builder()
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

        step("4. DB Category: проверка удаления категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuid(ctx.createGameCategoryResponse.getBody().getId());

            assertTrue(category.isEmpty(), "Категория с данным uuid должна быть удалена из БД");
        });

        step("5. Kafka: platform отправляет сообщение о создании категории в Kafka, в топик _core.gambling.v3.Game", () -> {
            ctx.gameCategoryEvent = kafkaClient.expect(GameCategoryEvent.class)
                    .with("message.eventType", GameEventType.CATEGORY.getValue())
                    .with("category.uuid", ctx.createGameCategoryResponse.getBody().getId())
                    .fetch();

            assertAll("Проверяем сообщение в топике Kafka",
                    () -> assertNotNull(ctx.gameCategoryEvent, "Должно быть сообщение в топике"),
                    () -> assertEquals(
                            GameEventType.CATEGORY, ctx.gameCategoryEvent.getMessage().getEventType(),
                            "Тип события в Kafka должен быть " + GameEventType.CATEGORY
                    ),
                    () -> assertEquals(
                            ctx.createGameCategoryResponse.getBody().getId(),
                            ctx.gameCategoryEvent.getCategory().getUuid(),
                            "UUID категории в Kafka должен совпадать с UUID из ответа"
                    ),
                    // вопрос - зачем еще раз передавать название, но нет алиаса
                    () -> assertNotNull(ctx.gameCategoryEvent.getCategory().getName()),
                    () -> assertEquals(
                            ctx.createGameCategoryRequest.getNames(),
                            ctx.gameCategoryEvent.getCategory().getLocalizedNames(),
                            "Localized names в Kafka должны совпадать с запросом"
                    ),
                    () -> assertEquals(
                            ctx.createGameCategoryRequest.getType().getValue(),
                            ctx.gameCategoryEvent.getCategory().getType(),
                            "Тип должен быть как в запросе"
                    ),
                    () -> assertEquals(
                            "disabled",
                            ctx.gameCategoryEvent.getCategory().getStatus(),
                            "Статус созданной категории по умолчанию должен быть disabled"
                    )
            );
        });
    }
}
