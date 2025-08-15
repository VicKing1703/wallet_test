package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.api.http.cap.dto.brand.CreateBrandRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.CreateBrandResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.DeleteBrandRequest;
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
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.*;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/brands")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform") @Tag("Brand")
class CreateBrandParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> brandParamsProvider() {
        return Stream.of(
                Arguments.of(
                        25, 0, 2, "длинны строк: название 25, описание 0, алиас 2"
                ),

                Arguments.of(
                        2, 50, 100, "длинны строк: название 2, описание 50, алиас 100"
                )
        );
    }

    @ParameterizedTest(name = "Создание категории: длина названия = {0}, описания = {1} и алиаса = {2}")
    @MethodSource("brandParamsProvider")
    @DisplayName("Создание бренда")
    void shouldCreateBrand(Integer titleLengths, Integer descriptionLengths, Integer aliasLengths) {

        final class TestContext {
            CreateBrandRequest createBrandRequest;
            ResponseEntity<CreateBrandResponse> createBrandResponse;
            DeleteBrandRequest deleteBrandRequest;
            ResponseEntity<Void> DeleteBrandResponse;
            String createdBrandId;
        }
        final TestContext ctx = new TestContext();

        step("Создание нового бренда", () -> {
            ctx.createBrandRequest = CreateBrandRequest.builder()
                    .sort(1)
                    .alias(get(ALIAS, aliasLengths))
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, titleLengths)))
                    .description(get(LETTERS, descriptionLengths))
                    .build();

            ctx.createBrandResponse = capAdminClient.createBrand(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createBrandRequest
            );

            assertAll("Проверка ответа создания бренда",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode(),
                            () -> "Ожидался код 200, но получен: " + ctx.createBrandResponse.getStatusCode()
                    ),
                    () -> assertNotNull(ctx.createBrandResponse.getBody().getId(),
                            "ID категории не должен быть null"
                    )
            );

            ctx.createdBrandId = ctx.createBrandResponse.getBody().getId();

        });

        step("DB Brand: проверка создания бренда", () -> {
            var brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createdBrandId);
            assertAll("Проверка ",
                    () -> assertEquals(brand.getUuid(), ctx.createdBrandId)
            );
        });

        step("Постусловие: Удаление бренда по ID", () -> {

            ctx.deleteBrandRequest = DeleteBrandRequest.builder().id(ctx.createdBrandId).build();

            ctx.DeleteBrandResponse = capAdminClient.deleteBrand(
                    ctx.createdBrandId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(HttpStatus.NO_CONTENT, ctx.DeleteBrandResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });
    }
}