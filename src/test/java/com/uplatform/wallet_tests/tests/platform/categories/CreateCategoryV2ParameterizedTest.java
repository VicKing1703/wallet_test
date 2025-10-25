package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.enums.CategoryTypeV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.CreateCategoryRequestV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.CreateCategoryResponseV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.DeleteCategoryRequestV2;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameCategoryEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.enums.GameEventType;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
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

import static com.uplatform.wallet_tests.api.db.entity.core.enums.GameCategoryStatus.DISABLED;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.ALIAS;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.TITLE;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Этот параметризованный тест проверяет API CAP на успешность создания игровых категорий типов:
 * категория и коллекция, с разной длинной названия и алиаса.
 *
 * <h3> Сценарий: Успешное создание игровой категории.</h3>
 * <ol>
 *      <li><b>Создание новой категории:</b>
 *  Создание новой категории через CAP, по ручке {@code POST /_cap/api/v2/categories}.
 *  В ответе код 200 и уникальный uuid созданной категории</li>
 *      <li><b>Нахождение созданной категории в БД:</b>
 *  В БД {@code `_core`.game_category} есть запись с uuid из ответа на создание категории,
 *  у записи entityType как переданный, одинаковые алиасы</li>
 *      <li><b>Поиск сообщения в Кафке о создании категории: </b>
 *  Проверяем в топике {@code core.gambling.v3.Game} сообщение о создании категории, что его uuid соответствуют,
 *  тип категории, статус и имена</li>
 *      <li><b>Постусловие: удаление созданной категории.</b> {@link DeleteCategoryTest}</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory") @Tag("GameCategoryV2") @Tag("CreateGameCategoryV2")
@Execution(ExecutionMode.SAME_THREAD)
class CreateCategoryV2ParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> categoryParams() {
        return Stream.of(
                Arguments.of(
                        CategoryTypeV2.CATEGORY, 25, 2,
                        "Тип категории: вертикальная, длинны строк: названия - 25, алиас - 2"
                ),
                Arguments.of(
                        CategoryTypeV2.COLLECTION, 2, 25,
                        "Тип категории: горизонтальная, длинны строк: названия - 2, алиас - 25"
                )
        );
    }

    @ParameterizedTest(name = ": тип = {0}, длина названия = {1}, алиаса = {2}")
    @MethodSource("categoryParams")
    @DisplayName("Создание категории V2")
    void shouldCreateGameCategoryV2(CategoryTypeV2 categoryTypeV2, Integer titleLengths, Integer aliasLengths) {

        final class TestContext {
            CreateCategoryRequestV2 createCategoryRequestV2;
            ResponseEntity<CreateCategoryResponseV2> createCategoryResponseV2;

            DeleteCategoryRequestV2 deleteCategoryRequestV2;
            ResponseEntity<Void> deleteCategoryResponseV2;

            GameCategoryEvent gameCategoryEvent;
        }

        final TestContext ctx = new TestContext();

        step("1. Создание новой категории", () -> {
            ctx.createCategoryRequestV2 = CreateCategoryRequestV2.builder()
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, titleLengths)))
                    .alias(get(ALIAS, aliasLengths))
                    .sort(1)
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .type(categoryTypeV2)
                    .build();

            ctx.createCategoryResponseV2 = capAdminClient.createCategoryV2(
                            utils.getAuthorizationHeader(),
                            configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                            ctx.createCategoryRequestV2
                    );

            assertAll("Проверка ответа создания категории",
                    () -> assertEquals(HttpStatus.OK, ctx.createCategoryResponseV2.getStatusCode()),
                    () -> assertNotNull(ctx.createCategoryResponseV2.getBody().getId())
            );
        });

        step("2. DB Category: проверка создания категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(
                    ctx.createCategoryResponseV2.getBody().getId()
            );

            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(ctx.createCategoryResponseV2.getBody().getId(), category.getUuid(),
                            "Uuid из ответа и в БД должны быть одинаковые"),
                    () -> assertEquals(ctx.createCategoryRequestV2.getType().getValue(), category.getEntityType(),
                            "Тип категории при создании и в поле entityType должны быть одинаковые"),
                    () -> assertEquals(ctx.createCategoryRequestV2.getAlias(), category.getAlias(),
                            "Алиас из запроса и в БД должны быть одинаковые"),
                    () -> assertEquals(DISABLED.statusId,
                            category.getStatusId(),
                            "Статус созданной категории по умолчанию должен быть " + DISABLED.statusId
                    )
            );
        });

        step("3. Kafka: platform отправляет сообщение о создании категории в Kafka, в топик _core.gambling.v3.Game", () -> {
            ctx.gameCategoryEvent = kafkaClient.expect(GameCategoryEvent.class)
                    .with("message.eventType", GameEventType.CATEGORY.getValue())
                    .with("category.uuid", ctx.createCategoryResponseV2.getBody().getId())
                    .fetch();

            assertAll("Проверяем сообщение в топике Kafka",
                    () -> assertNotNull(ctx.gameCategoryEvent, "Должно быть сообщение в топике"),
                    () -> assertEquals(
                            GameEventType.CATEGORY, ctx.gameCategoryEvent.getMessage().getEventType(),
                            "Тип события в Kafka должен быть " + GameEventType.CATEGORY
                    ),
                    () -> assertEquals(
                            ctx.createCategoryResponseV2.getBody().getId(),
                            ctx.gameCategoryEvent.getCategory().getUuid(),
                            "UUID категории в Kafka должен совпадать с UUID из ответа"
                    ),
                    // вопрос - зачем еще раз передавать название, но нет алиаса
                    () -> assertNotNull(ctx.gameCategoryEvent.getCategory().getName()),
                    () -> assertEquals(
                            ctx.createCategoryRequestV2.getNames(),
                            ctx.gameCategoryEvent.getCategory().getLocalizedNames(),
                            "Localized names в Kafka должны совпадать с запросом"
                    ),
                    () -> assertEquals(
                            ctx.createCategoryRequestV2.getType().getValue(),
                            ctx.gameCategoryEvent.getCategory().getType(),
                            "Тип должен быть как в запросе"
                    ),
                    () -> assertEquals(
                            DISABLED.status,
                            ctx.gameCategoryEvent.getCategory().getStatus(),
                            "Статус созданной категории по умолчанию должен быть " + DISABLED.status
                    )
            );
        });

        step("4. Постусловие. Удаление категории по uuid", () -> {
            ctx.deleteCategoryRequestV2 = DeleteCategoryRequestV2
                    .builder()
                    .id(ctx.createCategoryResponseV2.getBody().getId())
                    .build();

            ctx.deleteCategoryResponseV2 = capAdminClient.deleteCategoryV2(
                    ctx.createCategoryResponseV2.getBody().getId(),
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );


            assertEquals(HttpStatus.NO_CONTENT, ctx.deleteCategoryResponseV2.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });

    }
}
