package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGameCategory;
import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.*;
import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameCategoryEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.enums.GameEventType;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.tests.util.utils.RetryUtils;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Этот тест проверяет API CAP на успешность изменения игровой категории.
 *
 * <h3> Сценарий: Успешное изменение игровой категории.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание новой категории.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданной категории в БД.</b>
 *  {@link CreateGameCategoryParameterizedTest}</li>
 *      <li><b>Изменение данных категории:</b>
 *  Изменение названия, алиаса и типа категории по API CAP, по ручке {@code PATCH  /_cap/api/v1/categories/{uuid}}</li>
 *      <li><b>Проверка обновления категории в БД: </b>{@code `_core`.game_category}</li>
 *      <li><b>Поиск сообщения в Кафке о создании категории: </b>
 * Проверяем в топике {@code core.gambling.v3.Game} сообщение об изменении категории, что его uuid соответствуют,
 * тип категории, статус и имена</li>
 *      <li><b>Постусловие: удаление созданной категории.</b> {@link DeleteGameCategoryTest}</li>
 * </ol>
 */

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory") @Tag("PatchGameCategory")
@Execution(ExecutionMode.SAME_THREAD)
public class PatchGameCategoryTest extends BaseTest {

    static final class TestContext {
        CreateGameCategoryRequest createGameCategoryRequest;
        ResponseEntity<CreateGameCategoryResponse> createGameCategoryResponse;

        PatchGameCategoryRequest patchGameCategoryRequest;
        ResponseEntity<PatchGameCategoryResponse> patchGameCategoryResponse;

        DeleteGameCategoryRequest deleteGameCategoryRequest;
        ResponseEntity<Void> deleteGameCategoryResponse;

        CoreGameCategory category;

        GameCategoryEvent gameCategoryEvent;
    }

    private final TestContext ctx = new TestContext();

    @BeforeEach
    void setUp() {

        step("1. Предусловие. Создание категории", () -> {
            ctx.createGameCategoryRequest = CreateGameCategoryRequest.builder()
                    .alias(get(ALIAS, 7))
                    .type(CategoryType.VERTICAL)
                    .sort(99)
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
                    "Проверяем код ответа и тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createGameCategoryResponse.getBody().getId())
            );

        });

        step("2. Предусловие. DB Category: проверка создания категории", () -> {
            ctx.category = coreDatabaseClient.
                    findCategoryByUuidOrFail(ctx.createGameCategoryResponse.getBody().getId());

            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(ctx.category.getUuid(), ctx.createGameCategoryResponse.getBody().getId())
            );
        });
    };

    @Test
    @DisplayName("Изменение категории")
    void shouldPatchCategory() {

        step("3. Изменение категории", () -> {
            ctx.patchGameCategoryRequest = PatchGameCategoryRequest
                    .builder()
                    .alias(get(ALIAS, 11))
                    .type(CategoryType.HORIZONTAL)
                    .sort(100)
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 11)))
                    .build();

            ctx.patchGameCategoryResponse = capAdminClient.patchGameCategory(
                    ctx.createGameCategoryResponse.getBody().getId(),
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.patchGameCategoryRequest
            );

            assertAll(
                    "Проверяем код ответа и тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.patchGameCategoryResponse.getBody())
            );
        });

        step("4. Проверяем изменения категории в БД", () -> {
            ctx.category = coreDatabaseClient.
                    findCategoryByUuidOrFail(ctx.createGameCategoryResponse.getBody().getId());

            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(ctx.category.getUuid(), ctx.createGameCategoryResponse.getBody().getId()),
                    () -> assertEquals(ctx.patchGameCategoryRequest.getType().getValue(),
                            ctx.category.getType(),
                            "Тип категории должен измениться на " + CategoryType.HORIZONTAL
                    ),
                    () -> assertEquals(ctx.patchGameCategoryRequest.getAlias(), ctx.category.getAlias(),
                            "Alias в БД должен обновиться"
                    ),
                    () -> assertEquals(ctx.patchGameCategoryRequest.getNames().get(LangEnum.RUSSIAN),
                            ctx.category.getLocalizedNames().get("ru"),
                            "Localized names в БД должны обновиться"
                    ),
                    () -> assertNotEquals(ctx.category.getCreatedAt(), ctx.category.getUpdatedAt(),
                            "После PATCH поле updatedAt в БД должно быть != createdAt")
            );
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
                            ctx.patchGameCategoryRequest.getNames(),
                            ctx.gameCategoryEvent.getCategory().getLocalizedNames(),
                            "Localized names в Kafka должны совпадать с запросом"
                    ),
                    () -> assertEquals(
                            ctx.patchGameCategoryRequest.getType().getValue(),
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

    @AfterEach
    void teatDown() {

        step("6. Постусловие. Удаление категории по ID", () -> {
            ctx.deleteGameCategoryRequest = DeleteGameCategoryRequest.
                    builder()
                    .id(ctx.createGameCategoryResponse.getBody().getId())
                    .build();

            ctx.deleteGameCategoryResponse = capAdminClient.deleteGameCategory(
                    ctx.createGameCategoryResponse.getBody().getId(),
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
