package com.uplatform.wallet_tests.tests.platform.categories;

import com.uplatform.wallet_tests.api.http.cap.dto.category.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.category.CreateCategoryResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.*;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/categories")
@Suite("Позитивный сценарий: Действия с категориями")
@Tag("Platform")
class CreateCategoryParameterizedTest extends BaseParameterizedTest {
    static Stream<Arguments> categoryParamsProvider() {
        return Stream.of(
                Arguments.of(
                        10
                ),

                Arguments.of(
                        20
                )
        );
    }

    @ParameterizedTest(name = "Длина строки = {0}")
    @MethodSource("categoryParamsProvider")
    @DisplayName("Создание категории")
    void shouldCreateCategory(Integer categoryTitleLengths) {

        final class TestContext {
            CreateCategoryRequest  createCategoryRequest;
            ResponseEntity<CreateCategoryResponse> createCategoryResponse;
        }
        final TestContext ctx = new TestContext();

        step("Создание новой категории", () -> {
            ctx.createCategoryRequest = CreateCategoryRequest.builder()
                    .alias(get(ALIAS))
                    .type(CategoryType.VERTICAL)
                    .sort(1)
                    .groupId(configProvider.getEnvironmentConfig().getPlatform().getGroupId())
                    .projectId(configProvider.getEnvironmentConfig().getPlatform().getNodeId())
                    .names(Map.of(LangEnum.RUSSIAN, get(CATEGORY_TITLE, categoryTitleLengths)))
                    .build();

            ctx.createCategoryResponse = capAdminClient.createCategory(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getGroupId(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    configProvider.getEnvironmentConfig().getApi().getCapCredentials().getUsername(),
                    ctx.createCategoryRequest);

            assertAll(
              "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createCategoryResponse.getBody().getId())
            );
        });
    }
}