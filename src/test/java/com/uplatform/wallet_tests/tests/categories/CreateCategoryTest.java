package com.uplatform.wallet_tests.tests.categories;

import com.fasterxml.jackson.core.type.TypeReference;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.core.GameCategory;
import com.uplatform.wallet_tests.api.http.cap.dto.LocalizedName;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryResponse;
import com.uplatform.wallet_tests.api.kafka.dto.GameCategoryMessage;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.api.db.entity.core.enums.GameCategoryStatus.DISABLED;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.ALIAS;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.NAME;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный параметризованный тест, проверяющий позитивные сценарии создания категорий игр через CAP API:
 * {@code POST /_cap/api/v1/categories}.
 *
 * <p><b>Идея теста:</b> Гарантировать сквозную консистентность данных при выполнении одной из ключевых административных функций —
 * управления каталогом игр. Тест подтверждает, что создание новой категории через CAP API корректно инициирует
 * всю цепочку событий: от записи в базу данных до публикации сообщения в Kafka. Это обеспечивает надежность
 * системы и гарантирует, что изменения, сделанные администратором, будут правильно отражены во всех зависимых сервисах.</p>
 *
 * <p><b>Сценарии тестирования:</b></p>
 * <p>Тестируется создание двух основных типов сущностей: обычная категория ({@code CATEGORY}) и коллекция ({@code COLLECTION}),
 * чтобы убедиться в правильной обработке каждого из них.</p>
 *
 * <p><b>Последовательность действий для каждого набора параметров:</b></p>
 * <ol>
 *   <li>Отправка запроса в CAP API для создания новой категории с указанием типа, локализованных имен, алиаса и порядка сортировки.</li>
 *   <li>Проверка успешного ответа от CAP API (HTTP 200 OK) и наличия UUID созданной категории в теле ответа.</li>
 *   <li>Проверка прямого сохранения новой категории в основной базе данных Core (таблица game_category) и валидация всех полей.</li>
 *   <li>Прослушивание и валидация события в Kafka-топике {@code core.gambling.v3.Game}, подтверждающего создание категории и содержащего корректные данные.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос на создание категории успешно обрабатывается (HTTP 200 OK), в ответе возвращается уникальный идентификатор (UUID).</li>
 *   <li>В базе данных Core создается новая запись в таблице {@code game_category} со всеми полями, соответствующими запросу, и статусом по умолчанию (DISABLED).</li>
 *   <li>В Kafka-топик {@code core.gambling.v3.Game} публикуется событие, содержащее полную и корректную информацию о созданной категории.</li>
 *   <li>Данные (тип, UUID, имена, статус) консистентны между ответом API, состоянием в БД и сообщением в Kafka.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Execution(ExecutionMode.SAME_THREAD)
@Epic("CAP")
@Feature("Управление списком игр")
@Suite("Создание категорий: Позитивные сценарии")
@Tag("CAP") @Tag("Platform")
class CreateCategoryTest extends BaseParameterizedTest {

    private static final int SORT_ORDER = 1;
    private static final String GAME_CATEGORY_EVENT_TYPE = "category";

    private String projectId;
    private String platformUserId;
    private String platformUsername;

    @BeforeAll
    void setupGlobalContext() {
        var envConfig = configProvider.getEnvironmentConfig();
        this.projectId = envConfig.getPlatform().getNodeId();
        this.platformUserId = HttpServiceHelper.getCapPlatformUserId(envConfig.getHttp());
        this.platformUsername = HttpServiceHelper.getCapPlatformUsername(envConfig.getHttp());
    }

    static Stream<Arguments> categoryTypeProvider() {
        return Stream.of(
                Arguments.of(CategoryType.CATEGORY),
                Arguments.of(CategoryType.COLLECTION)
        );
    }

    @ParameterizedTest(name = "Тип категории = {0}")
    @MethodSource("categoryTypeProvider")
    @DisplayName("Создание категории:")
    void shouldCreateCategory(CategoryType categoryType) {
        final class TestContext {
            CreateCategoryRequest request;
            CreateCategoryResponse responseBody;
            GameCategory gameCategory;
            Map<String, String> expectedLocalizedNames;
            GameCategoryMessage gameCategoryMessage;
        }
        final TestContext ctx = new TestContext();

        step("CAP API: Отправка запроса на создание категории игр", () -> {
            ctx.request = CreateCategoryRequest.builder()
                    .names(LocalizedName.builder()
                            .ru(get(NAME, 10))
                            .en(get(NAME, 10))
                            .lv(get(NAME, 10))
                            .build())
                    .alias(get(ALIAS, 12))
                    .sort(SORT_ORDER)
                    .projectId(projectId)
                    .type(categoryType)
                    .build();

            ctx.expectedLocalizedNames = objectMapper.convertValue(
                    ctx.request.getNames(),
                    new TypeReference<>() {}
            );

            var response = capAdminClient.createCategory(
                    utils.getAuthorizationHeader(),
                    platformUserId,
                    platformUsername,
                    ctx.request
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.create_category.status_code");
            ctx.responseBody = response.getBody();
            assertNotNull(ctx.responseBody, "cap_api.create_category.response_body");
            assertNotNull(ctx.responseBody.id(), "cap_api.create_category.id");
        });

        step("DB (Core): Проверка сохранения категории в таблице game_category", () -> {
            ctx.gameCategory = coreDatabaseClient.findGameCategoryByUuidOrFail(ctx.responseBody.id());

            assertAll("Проверка полей созданной категории в БД",
                    () -> assertEquals(ctx.responseBody.id(), ctx.gameCategory.getUuid(), "core_db.game_category.uuid"),
                    () -> assertEquals(ctx.request.getAlias(), ctx.gameCategory.getAlias(), "core_db.game_category.alias"),
                    () -> assertEquals(projectId, ctx.gameCategory.getProjectUuid(), "core_db.game_category.project_uuid"),
                    () -> assertEquals(ctx.request.getType().value(), ctx.gameCategory.getEntityType(), "core_db.game_category.entity_type"),
                    () -> assertEquals(DISABLED.statusId, ctx.gameCategory.getStatusId(), "core_db.game_category.status_id"),
                    () -> assertEquals(SORT_ORDER, ctx.gameCategory.getEntitySort(), "core_db.game_category.entity_sort"),
                    () -> assertEquals(ctx.expectedLocalizedNames, ctx.gameCategory.getLocalizedNames(), "core_db.game_category.localized_names"),
                    () -> assertTrue(ctx.gameCategory.getCreatedAt() > 0, "core_db.game_category.created_at"),
                    () -> assertTrue(ctx.gameCategory.getUpdatedAt() >= ctx.gameCategory.getCreatedAt(), "core_db.game_category.updated_at"),
                    () -> assertTrue(ctx.gameCategory.getProjectGroupUuid().isBlank(), "core_db.game_category.project_group_uuid"),
                    () -> assertFalse(ctx.gameCategory.isDefault(), "core_db.game_category.is_default"),
                    () -> assertNull(ctx.gameCategory.getParentUuid(), "core_db.game_category.parent_uuid"),
                    () -> assertFalse(ctx.gameCategory.isCms(), "core_db.game_category.cms")
            );
        });

        step("Kafka: Проверка события создания категории в топике core.gambling.v3.Game", () -> {
            ctx.gameCategoryMessage = kafkaClient.expect(GameCategoryMessage.class)
                    .with("category.uuid", ctx.responseBody.id())
                    .unique()
                    .fetch();

            var kafkaCategory = ctx.gameCategoryMessage.category();
            assertNotNull(kafkaCategory, "kafka.game_category_event.category");

            var messageEnvelope = ctx.gameCategoryMessage.message();
            assertNotNull(messageEnvelope, "kafka.game_category_event.message");

            assertAll("Проверка полей Kafka-сообщения о создании категории",
                    () -> assertEquals(ctx.responseBody.id(), kafkaCategory.uuid(), "kafka.game_category_event.category.uuid"),
                    () -> assertEquals(ctx.request.getType().value(), kafkaCategory.type(), "kafka.game_category_event.category.type"),
                    () -> assertEquals(DISABLED.status, kafkaCategory.status(), "kafka.game_category_event.category.status"),
                    () -> assertEquals(ctx.expectedLocalizedNames, kafkaCategory.localizedNames(), "kafka.game_category_event.category.localized_names"),
                    //ToDo тут какой-то артефакт name дублирует данные из localized_names
                    () -> assertEquals(ctx.request.getNames().getRu(), kafkaCategory.name(), "kafka.game_category_event.category.name"),
                    () -> assertEquals(GAME_CATEGORY_EVENT_TYPE, messageEnvelope.eventType(), "kafka.game_category_event.message.event_type")
            );
        });
    }
}