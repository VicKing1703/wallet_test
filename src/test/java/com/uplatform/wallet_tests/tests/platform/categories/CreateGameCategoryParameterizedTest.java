package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.CreateGameCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.CreateGameCategoryResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.DeleteGameCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameBrandEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameCategoryEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.enums.GameEventType;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.tests.util.utils.RetryUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
 * Этот параметризованный тест проверяет API CAP на успешность создания игровых категорий типов:
 * вертикальная, горизонтальная и навигационная панель, с разной длинной названия и алиаса.
 *
 * <h3> Сценарий: Успешное создание игровой категории.</h3>
 * <ol>
 *      <li><b>Создание новой категории:</b>
 *  Создание новой категории через CAP, по ручке {@code POST /_cap/api/v1/categories}.
 *  В ответе код 200 и уникальный uuid созданной категории</li>
 *      <li><b>Нахождение созданной категории в БД:</b>
 *  В БД {@code `_core`.game_category} есть запись с uuid из ответа на создание категории</li>
 *      <li><b>Поиск сообщения в Кафке о создании категории: </b>
 *  Проверяем в топике {@code core.gambling.v3.Game} сообщение о создании категории, что его uuid соответствуют,
 *  тип категории, статус и имена</li>
 *      <li><b>Постусловие: удаление созданной категории.</b> {@link DeleteGameCategoryTest}</li>
 * </ol>
 */

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory") @Tag("CreateGameCategory")
@Execution(ExecutionMode.SAME_THREAD)
class CreateGameCategoryParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> categoryParams() {
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

    @ParameterizedTest(name = ": тип = {0}, длина названия = {1}, алиаса = {2}")
    @MethodSource("categoryParams")
    @DisplayName("Создание категории")
    void shouldCreateGameCategory(CategoryType categoryType, Integer titleLengths, Integer aliasLengths) {

        final class TestContext {
            CreateGameCategoryRequest createGameCategoryRequest;
            ResponseEntity<CreateGameCategoryResponse> createGameCategoryResponse;

            DeleteGameCategoryRequest deleteGameCategoryRequest;
            ResponseEntity<Void> deleteGameCategoryResponse;

            GameCategoryEvent gameCategoryEvent;
        }

        final TestContext ctx = new TestContext();

        step("1. Создание новой категории", () -> {
            ctx.createGameCategoryRequest = CreateGameCategoryRequest.builder()
                    .alias(get(ALIAS, aliasLengths))
                    .type(categoryType)
                    .sort(66)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, titleLengths)))
                    .build();

            ctx.createGameCategoryResponse =
                    capAdminClient.createGameCategory(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createGameCategoryRequest
            );

            assertAll("Проверка ответа создания категории",
                    () -> assertEquals(HttpStatus.OK, ctx.createGameCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createGameCategoryResponse.getBody().getId())
            );
        });


        step("2. DB Category: проверка создания категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(
                    ctx.createGameCategoryResponse.getBody().getId()
            );

            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(category.getUuid(), ctx.createGameCategoryResponse.getBody().getId(),
                            "Uuid из ответа и в БД должны быть одинаковые")
            );
        });

        step("3. Kafka: platform отправляет сообщение о создании категории в Kafka, в топик _core.gambling.v3.Game", () -> {
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

        step("4. Постусловие. Удаление категории по uuid", () -> {
            ctx.deleteGameCategoryRequest = DeleteGameCategoryRequest
                    .builder()
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
    }
}