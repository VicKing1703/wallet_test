package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.api.http.cap.dto.brand.CreateBrandRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.CreateBrandResponse;
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
@Feature("/brands")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform")
class CreateBrandParameterizedTest extends BaseParameterizedTest {
    static Stream<Arguments> brandParamsProvider() {
        return Stream.of(
                Arguments.of(
                        10, 0
                ),

                Arguments.of(
                        20, 100
                )
        );
    }

    @ParameterizedTest(name = "Длинна строки названия = {0}")
    @MethodSource("brandParamsProvider")
    @DisplayName("Создание бренда")
    void shouldCreateBrand(Integer brandTitleLengths, Integer brandDescriptionLengths) {

        final class TestContext {
            CreateBrandRequest createBrandRequest;
            ResponseEntity<CreateBrandResponse> createBrandResponse;
        }
        final TestContext ctx = new TestContext();

        step("Создание нового бренда", () -> {
            ctx.createBrandRequest = CreateBrandRequest.builder()
                    .sort(1)
                    .alias(get(ALIAS))
                    .names(Map.of(LangEnum.RUSSIAN, get(BRAND_TITLE, brandTitleLengths)))
                    .description(Map.of(LangEnum.RUSSIAN, get(CYRILLIC_LETTERS, brandDescriptionLengths)).toString())
                    .build();

            ctx.createBrandResponse = capAdminClient.createBrand(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createBrandRequest);

            assertAll(
                    "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createBrandResponse.getBody().getId())
            );
        });
    }
}