package com.uplatform.wallet_tests.tests.categories;

import com.fasterxml.jackson.core.type.TypeReference;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
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
 * Интеграционный параметризованный тест, проверяющий позитивные сценарии удаления категорий игр через CAP API:
 * {@code DELETE /_cap/api/v1/categories/{uuid}}.
 *
 * <p><b>Идея теста:</b> Продемонстрировать полную сквозную надежность системы при выполнении деструктивной административной
 * операции — удаления категории. Тест гарантирует, что запрос на удаление через единую точку входа (CAP API)
 * корректно распространяется по всей системе: запись физически удаляется из основной базы данных, а в Kafka отправляется
 * соответствующее событие. Это подтверждает, что состояние каталога игр остается консистентным во всех сервисах.</p>
 *
 * <p><b>Сценарии тестирования:</b></p>
 * <p>Тестируется удаление двух основных типов сущностей: обычной категории ({@code CATEGORY}) и коллекции ({@code COLLECTION}),
 * чтобы убедиться в правильной обработке каждого из них.</p>
 *
 * <p><b>Последовательность действий для каждого набора параметров:</b></p>
 * <ol>
 *   <li>Создание новой категории игр через CAP API (как предусловие для теста).</li>
 *   <li>Отправка запроса в CAP API для удаления только что созданной категории.</li>
 *   <li>Проверка успешного ответа от CAP API (HTTP 204 NO CONTENT).</li>
 *   <li>Проверка того, что запись о категории была физически удалена из основной базы данных Core.</li>
 *   <li>Прослушивание и валидация события в Kafka-топике {@code core.gambling.v3.Game}, подтверждающего факт удаления категории.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос на удаление категории успешно обрабатывается (HTTP 204 NO CONTENT).</li>
 *   <li>Запись в таблице {@code game_category} в базе данных Core полностью удаляется.</li>
 *   <li>В Kafka-топик {@code core.gambling.v3.Game} публикуется событие, содержащее информацию об удаленной категории.</li>
 *   <li>Система остается в консистентном состоянии после выполнения операции.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Execution(ExecutionMode.SAME_THREAD)
@Epic("CAP")
@Feature("Категории")
@Suite("Удаление категорий: Позитивные сценарии")
@Tag("CAP") @Tag("Platform")
class DeleteCategoryTest extends BaseParameterizedTest {

    private static final int SORT_ORDER = 1;

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
    @DisplayName("Удаление категории:")
    void shouldDeleteCategory(CategoryType categoryType) {
        final class TestContext {
            CreateCategoryRequest createRequest;
            CreateCategoryResponse createResponse;
            Map<String, String> expectedLocalizedNames;
            GameCategoryMessage gameCategoryMessage;
        }
        final TestContext ctx = new TestContext();

        step("Pre-condition: Создание категории для последующего удаления", () -> {
            ctx.createRequest = CreateCategoryRequest.builder()
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
                    ctx.createRequest.getNames(),
                    new TypeReference<>() {}
            );

            var response = capAdminClient.createCategory(
                    utils.getAuthorizationHeader(),
                    platformUserId,
                    platformUsername,
                    ctx.createRequest
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "precondition.create_category.status_code");
            ctx.createResponse = response.getBody();
            assertNotNull(ctx.createResponse, "precondition.create_category.response_body");
            assertNotNull(ctx.createResponse.id(), "precondition.create_category.id");
        });

        step("CAP API: Отправка запроса на удаление категории", () -> {
            var response = capAdminClient.deleteCategory(
                    ctx.createResponse.id(),
                    utils.getAuthorizationHeader(),
                    platformUserId,
                    platformUsername
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.delete_category.status_code");
        });

        step("DB (Core): Проверка физического удаления категории из таблицы game_category", () -> {
            var remainingRecords = coreDatabaseClient.waitForGameCategoryDeletionOrFail(ctx.createResponse.id());
            assertEquals(0L, remainingRecords, "core_db.game_category.remaining_rows");
        });

        step("Kafka: Проверка события об удалении категории в топике core.gambling.v3.Game", () -> {
            ctx.gameCategoryMessage = kafkaClient.expect(GameCategoryMessage.class)
                    .with("category.uuid", ctx.createResponse.id())
                    .fetch();

            assertNotNull(ctx.gameCategoryMessage, "kafka.game_category_event.message");
            var messageEnvelope = ctx.gameCategoryMessage.message();
            assertNotNull(messageEnvelope, "kafka.game_category_event.message_envelope");
            var kafkaCategory = ctx.gameCategoryMessage.category();
            assertNotNull(kafkaCategory, "kafka.game_category_event.category");

            assertAll("Проверка полей Kafka-сообщения об удалении категории",
                    () -> assertEquals(ctx.createResponse.id(), kafkaCategory.uuid(), "kafka.game_category_event.category.uuid"),
                    () -> assertEquals(ctx.createRequest.getType().value(), kafkaCategory.type(), "kafka.game_category_event.category.type"),
                    () -> assertEquals(ctx.expectedLocalizedNames, kafkaCategory.localizedNames(), "kafka.game_category_event.category.localized_names"),
                    () -> assertEquals(DISABLED.status, kafkaCategory.status(), "kafka.game_category_event.category.status"),
                    () -> assertEquals(CategoryType.CATEGORY.value(), messageEnvelope.eventType(), "kafka.game_category_event.message.event_type"),
                    //ToDo тут какой-то артефакт name дублирует данные из localized_names
                    () -> assertEquals(ctx.createRequest.getNames().getRu(), kafkaCategory.name(), "kafka.game_category_event.category.name")
            );
        });
    }
}