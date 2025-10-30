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
 * Этот параметризованный тест проверяет API CAP на успешность создания игровой подкатегории,
 * с разной длинной названия и алиаса.
 *
 * <h3> Сценарий: Успешное создание игровой подкатегории.</h3>
 * <ol>
 *      <li><b>Создание новой категории:</b>
 *  Создание новой категории через CAP, по ручке {@code POST /_cap/api/v2/categories}.
 *  В ответе код 200 и уникальный uuid созданной категории</li>
 *      <li><b>Создание подкатегории:</b>
 *  Создание новой подкатегории через CAP, по ручке {@code POST /_cap/api/v2/categories}, с указанием {@code parentCategoryId}.
 *  В ответе код 200 и уникальный uuid созданной категории</li>
 *      <li><b>Нахождение созданной подкатегории в БД:</b>
 *  В БД {@code `_core`.game_category} есть запись с uuid из ответа на создание категории,
 *  у записи entityType как переданный, одинаковые алиасы, есть родительская категория</li>
 *      <li><b>Поиск сообщения в Кафке о создании подкатегории: </b>
 *  Проверяем в топике {@code core.gambling.v3.Game} сообщение о создании подкатегории, что его uuid соответствуют,
 *  тип категории, статус и имена</li>
 *      <li><b>Постусловие: удаление созданной категории.</b> {@link DeleteCategoryV2Test}</li>
 * </ol>
 */

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/categories")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform") @Tag("GameCategory") @Tag("GameCategoryV2") @Tag("CreateGameSubcategoryV2")
@Execution(ExecutionMode.SAME_THREAD)
class CreateSubcategoryV2ParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> categoryParams() {
        return Stream.of(
                Arguments.of(
                        25, 2,
                        "Длинны строк: названия - 25, алиас - 2"
                ),
                Arguments.of(
                        2, 25,
                        "Длинны строк: названия - 2, алиас - 25"
                )
        );
    }

    @ParameterizedTest(name = ": длина названия = {0}, алиаса = {1}")
    @MethodSource("categoryParams")
    @DisplayName("Создание подкатегории V2")
    void shouldCreateGameSubcategoryV2(Integer titleLengths, Integer aliasLengths) {

        final class TestContext {
            CreateCategoryRequestV2 createCategoryRequestV2;
            ResponseEntity<CreateCategoryResponseV2> createCategoryResponseV2;

            CreateCategoryRequestV2 createSubcategoryRequestV2;
            ResponseEntity<CreateCategoryResponseV2> createSubcategoryResponseV2;

            DeleteCategoryRequestV2 deleteCategoryRequestV2;
            ResponseEntity<Void> deleteCategoryResponseV2;

            GameCategoryEvent gameCategoryEvent;
        }

        final TestContext ctx = new TestContext();

        step("1. Создание новой категории", () -> {
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
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, titleLengths)))
                    .alias(get(ALIAS, aliasLengths))
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
                    () -> assertEquals(HttpStatus.OK, ctx.createSubcategoryResponseV2.getStatusCode()),
                    () -> assertNotNull(ctx.createSubcategoryResponseV2.getBody().getId())
            );
        });

        step("3. DB Category: проверка создания категории", () -> {
            var category = coreDatabaseClient.findCategoryByUuidOrFail(
                    ctx.createSubcategoryResponseV2.getBody().getId()
            );

            assertAll("Проверка что есть категория с uuid как у созданной",
                    () -> assertEquals(ctx.createSubcategoryResponseV2.getBody().getId(),
                            category.getUuid(),
                            "Uuid в БД должны быть " + ctx.createSubcategoryResponseV2.getBody().getId()
                    ),
                    () -> assertEquals(ctx.createSubcategoryRequestV2.getType().getValue(),
                            category.getEntityType(),
                            "Тип категории в поле entityType должны быть " + ctx.createSubcategoryRequestV2.getType().getValue()
                    ),
                    () -> assertEquals(ctx.createSubcategoryRequestV2.getAlias(),
                            category.getAlias(),
                            "Алиас в БД должны быть " + ctx.createSubcategoryRequestV2.getAlias()
                    ),
                    () -> assertEquals(ctx.createCategoryResponseV2.getBody().getId(),
                            category.getParentUuid(),
                            "Uuid категории из первого шага должен быть равен parentUuid"
                    ),
                    () -> assertEquals(DISABLED.statusId,
                            category.getStatusId(),
                            "Статус созданной категории по умолчанию должен быть " + DISABLED.statusId
                    )
            );
        });

        step("3. Kafka: platform отправляет сообщение о создании категории в Kafka, в топик _core.gambling.v3.Game", () -> {
            ctx.gameCategoryEvent = kafkaClient.expect(GameCategoryEvent.class)
                    .with("message.eventType", GameEventType.CATEGORY.getValue())
                    .with("category.uuid", ctx.createSubcategoryResponseV2.getBody().getId())
                    .fetch();

            assertAll("Проверяем сообщение в топике Kafka",
                    () -> assertNotNull(ctx.gameCategoryEvent, "Должно быть сообщение в топике"),
                    () -> assertEquals(GameEventType.CATEGORY,
                            ctx.gameCategoryEvent.getMessage().getEventType(),
                            "Тип события в Kafka должен быть " + GameEventType.CATEGORY
                    ),
                    () -> assertEquals(ctx.createSubcategoryResponseV2.getBody().getId(),
                            ctx.gameCategoryEvent.getCategory().getUuid(),
                            "UUID категории в Kafka должен быть " + ctx.createSubcategoryResponseV2.getBody().getId()
                    ),
                    // вопрос - зачем еще раз передавать название, но нет алиаса
                    () -> assertNotNull(ctx.gameCategoryEvent.getCategory().getName()),
                    () -> assertEquals(ctx.createSubcategoryRequestV2.getNames(),
                            ctx.gameCategoryEvent.getCategory().getLocalizedNames(),
                            "Localized names в Kafka должн быть " + ctx.createSubcategoryRequestV2.getNames()
                    ),
                    () -> assertEquals(ctx.createSubcategoryRequestV2.getType().getValue(),
                            ctx.gameCategoryEvent.getCategory().getType(),
                            "Тип должен быть " + ctx.createSubcategoryRequestV2.getType().getValue()
                    ),
                    () -> assertEquals(DISABLED.status,
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
                    "Ожидаем статус " + HttpStatus.NO_CONTENT
            );
        });
    }
}
