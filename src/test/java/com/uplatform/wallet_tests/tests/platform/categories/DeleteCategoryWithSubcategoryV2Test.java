package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.enums.CategoryTypeV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.CreateCategoryRequestV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.CreateCategoryResponseV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.DeleteCategoryRequestV2;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameCategoryEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.enums.GameEventType;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static com.uplatform.wallet_tests.api.db.entity.core.enums.GameCategoryStatus.DISABLED;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.ALIAS;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.TITLE;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Этот тест проверяет API CAP на успешность удаления игровой категорий.
 *
 * <h3> Сценарий: Успешное удаление игровой категории.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание новой категории.</b>
 *  {@link CreateCategoryV2ParameterizedTest}</li>
 *      <li><b>Предусловие. Создание подкатегории {@link CreateSubcategoryV2ParameterizedTest}</li>
 *      <li><b>Удаление созданной категории.</b>
 *  Успешное удаление созданной категории по API CAP, по ручке {@code DELETE /_cap/api/v1/categories/{uuid}},
 *  по uuid из ответа на создание. В ответ получаем код 204</li>
 *      <li><b>Поиск сообщения в Кафке об удалении категории: </b>
 *  Проверяем в топике {@code core.gambling.v3.Game} сообщение об удалении категории, что его uuid соответствуют,
 *  тип категории, статус и имена</li>
 *      <li><b>Поиск сообщения в Кафке об удалении подкатегории: </b>
 *  Проверяем в топике {@code core.gambling.v3.Game} сообщение об удалении подкатегории, что его uuid соответствуют,
 *  тип подкатегории, статус и имена</li>
 *      <li><b>Проверка удаления категории в БД.</b>
 *  Проверка, что в БД {@code `_core`.game_category} отсутствует категория с uuid из создания категории</li>
 *      <li><b>Проверка удаления подкатегории в БД</b>
 *  Проверка, что в БД {@code `_core`.game_category} отсутствует подкатегория с uuid из создания подкатегории</li>
 * </ol>
 */

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories/{uuid}")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory") @Tag("DeleteGameCategoryWithSubcategoryV2")
@Execution(ExecutionMode.SAME_THREAD)
public class DeleteCategoryWithSubcategoryV2Test extends BaseTest {

    static final class TestContext {
        CreateCategoryRequestV2 createCategoryRequestV2;
        ResponseEntity<CreateCategoryResponseV2> createCategoryResponseV2;

        CreateCategoryRequestV2 createSubcategoryRequestV2;
        ResponseEntity<CreateCategoryResponseV2> createSubcategoryResponseV2;

        DeleteCategoryRequestV2 deleteCategoryRequestV2;
        ResponseEntity<Void> deleteCategoryResponseV2;

        GameCategoryEvent gameCategoryEvent;
        GameCategoryEvent gameSubcategoryEvent;
    }

    final TestContext ctx = new TestContext();

    @BeforeEach
    void stUp() {

        step("1. Предусловие. Создание категории", () -> {
            ctx.createCategoryRequestV2 = CreateCategoryRequestV2.builder()
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 5)))
                    .alias(get(ALIAS, 5))
                    .sort(1)
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .type(CategoryTypeV2.CATEGORY)
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

        step("2. Создание подкатегории", () -> {
            ctx.createSubcategoryRequestV2 = CreateCategoryRequestV2.builder()
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 7)))
                    .alias(get(ALIAS, 7))
                    .sort(1)
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .type(CategoryTypeV2.SUBCATEGORY)
                    .parentCategoryId(ctx.createCategoryResponseV2.getBody().getId())
                    .build();

            ctx.createSubcategoryResponseV2 = capAdminClient.createCategoryV2(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createSubcategoryRequestV2
            );

            assertAll("Проверка ответа создания подкатегории",
                    () -> assertEquals(HttpStatus.OK, ctx.createSubcategoryResponseV2.getStatusCode(),
                            "Ожидаем статус " + HttpStatus.OK),
                    () -> assertNotNull(ctx.createSubcategoryResponseV2.getBody().getId())
            );
        });
    }

    @Test
    @DisplayName("Удаление категории V2 по её uuid и подкатегории")
    void shouldDeleteGameCategory() {

        step("3. Удаление категории по ID", () -> {
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
                    "Ожидаем статус " + HttpStatus.NO_CONTENT
            );
        });

        step("4. Kafka: platform отправляет сообщение о удалении категории в Kafka, в топик _core.gambling.v3.Game", () -> {
            ctx.gameCategoryEvent = kafkaClient.expect(GameCategoryEvent.class)
                    .with("message.eventType", GameEventType.CATEGORY.getValue())
                    .with("category.uuid", ctx.createCategoryResponseV2.getBody().getId())
                    .fetch();

            assertAll("Проверяем сообщение в топике Kafka",
                    () -> assertNotNull(ctx.gameCategoryEvent,
                            "Должно быть сообщение в Кафке"
                    ),
                    () -> assertEquals(GameEventType.CATEGORY,
                            ctx.gameCategoryEvent.getMessage().getEventType(),
                            "Тип события в Kafka должен быть " + GameEventType.CATEGORY
                    ),
                    () -> assertEquals(ctx.createCategoryResponseV2.getBody().getId(),
                            ctx.gameCategoryEvent.getCategory().getUuid(),
                            "UUID категории в Kafka должен совпадать с UUID из ответа"
                    ),
                    // вопрос - зачем еще раз передавать название, но нет алиаса
                    () -> assertNotNull(ctx.gameCategoryEvent.getCategory().getName()),
                    () -> assertEquals(ctx.createCategoryRequestV2.getNames(),
                            ctx.gameCategoryEvent.getCategory().getLocalizedNames(),
                            "Localized names в Kafka должны совпадать с запросом"
                    ),
                    () -> assertEquals(ctx.createCategoryRequestV2.getType().getValue(),
                            ctx.gameCategoryEvent.getCategory().getType(),
                            "Тип должен быть как в запросе"
                    ),
                    () -> assertEquals(
                            DISABLED.status,
                            ctx.gameCategoryEvent.getCategory().getStatus(),
                            "Статус созданной категории по умолчанию должен быть disabled"
                    )
            );
        });

        step("5. Kafka: platform отправляет сообщение о удалении подкатегории в Kafka, в топик _core.gambling.v3.Game", () -> {
            ctx.gameSubcategoryEvent = kafkaClient.expect(GameCategoryEvent.class)
                    .with("message.eventType", GameEventType.CATEGORY.getValue())
                    .with("category.uuid", ctx.createSubcategoryResponseV2.getBody().getId())
                    .fetch();

            assertAll("Проверяем сообщение в топике Kafka",
                    () -> assertNotNull(ctx.gameSubcategoryEvent,
                            "Должно быть сообщение в Кафке"
                    ),
                    () -> assertEquals(GameEventType.CATEGORY,
                            ctx.gameSubcategoryEvent.getMessage().getEventType(),
                            "Тип события в Kafka должен быть " + GameEventType.CATEGORY
                    ),
                    () -> assertEquals(ctx.createSubcategoryResponseV2.getBody().getId(),
                            ctx.gameSubcategoryEvent.getCategory().getUuid(),
                            "UUID категории в Kafka должен быть " + ctx.createSubcategoryResponseV2.getBody().getId()
                    ),
                    // вопрос - зачем еще раз передавать название, но нет алиаса
                    () -> assertNotNull(ctx.gameSubcategoryEvent.getCategory().getName()),
                    () -> assertEquals(ctx.createSubcategoryRequestV2.getNames(),
                            ctx.gameSubcategoryEvent.getCategory().getLocalizedNames(),
                            "Localized names в Kafka должен быть " + ctx.createSubcategoryRequestV2.getNames()
                    ),
                    () -> assertEquals(ctx.createSubcategoryRequestV2.getType().getValue(),
                            ctx.gameSubcategoryEvent.getCategory().getType(),
                            "Тип должен быть " + ctx.createSubcategoryRequestV2.getType().getValue()
                    ),
                    () -> assertEquals(
                            DISABLED.status,
                            ctx.gameSubcategoryEvent.getCategory().getStatus(),
                            "Статус созданной категории по умолчанию должен быть " + DISABLED.status
                    )
            );
        });

        step("6. DB Category: проверка удаления категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuid(ctx.createCategoryResponseV2.getBody().getId());

            assertTrue(category.isEmpty(), "Категория с данным uuid должна быть удалена из БД");
        });

        step("7. DB Category: проверка удаления категории", () -> {
            var subcategory = coreDatabaseClient.findCategoryByUuid(ctx.createSubcategoryResponseV2.getBody().getId());

            assertTrue(subcategory.isEmpty(), "Категория с данным uuid должна быть удалена из БД");
        });
    }
}
