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

@Severity(SeverityLevel.CRITICAL)
@Execution(ExecutionMode.SAME_THREAD)
@Epic("CAP")
@Feature("Управление списком игр")
@Suite("Удаление категорий: Позитивные сценарии")
@Tag("CAP")
@Tag("Platform")
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
            long remainingRecords;
            GameCategoryMessage gameCategoryMessage;
        }
        final TestContext ctx = new TestContext();

        step("CAP API: Создание категории игр для последующего удаления", () -> {
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

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.create_category.status_code");
            ctx.createResponse = response.getBody();
            assertNotNull(ctx.createResponse, "cap_api.create_category.response_body");
            assertNotNull(ctx.createResponse.id(), "cap_api.create_category.id");
        });

        step("CAP API: Удаление категории игр", () -> {
            var response = capAdminClient.deleteCategory(
                    ctx.createResponse.id(),
                    utils.getAuthorizationHeader(),
                    platformUserId,
                    platformUsername
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.delete_category.status_code");
        });

        step("DB (Core): Проверка удаления категории из таблицы game_category", () -> {
            ctx.remainingRecords = coreDatabaseClient.waitForGameCategoryDeletionOrFail(ctx.createResponse.id());
            assertEquals(0L, ctx.remainingRecords, "core_db.game_category.remaining_rows");
        });

        step("Kafka: Проверка события удаления категории в топике core.gambling.v3.Game", () -> {
            ctx.gameCategoryMessage = kafkaClient.expect(GameCategoryMessage.class)
                    .with("category.uuid", ctx.createResponse.id())
                    .with("message.eventType", CategoryType.CATEGORY.value())
                    .fetch();

            assertNotNull(ctx.gameCategoryMessage, "kafka.game_category_event.message");
            var messageEnvelope = ctx.gameCategoryMessage.message();
            assertNotNull(messageEnvelope, "kafka.game_category_event.message_envelope");
            var kafkaCategory = ctx.gameCategoryMessage.category();
            assertNotNull(kafkaCategory, "kafka.game_category_event.category");

            assertAll("Проверка полей Kafka-сообщения об удалении категории",
                    () -> assertEquals(CategoryType.CATEGORY.value(), messageEnvelope.eventType(), "kafka.game_category_event.message.event_type"),
                    () -> assertEquals(ctx.createResponse.id(), kafkaCategory.uuid(), "kafka.game_category_event.category.uuid"),
                    () -> assertEquals(ctx.createRequest.getType().value(), kafkaCategory.type(), "kafka.game_category_event.category.type"),
                    () -> assertEquals(ctx.expectedLocalizedNames, kafkaCategory.localizedNames(), "kafka.game_category_event.category.localized_names"),
                    () -> assertEquals(ctx.createRequest.getNames().getRu(), kafkaCategory.name(), "kafka.game_category_event.category.name"),
                    () -> assertEquals(DISABLED.status, kafkaCategory.status(), "kafka.game_category_event.category.status")
            );
        });
    }
}
