package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.api.http.cap.dto.brand.*;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.allure.Suite;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.*;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Этот тест проверяет API CAP на получение данных бренда.
 *
 * <h3> Сценарий: Успешное получение информации о бренде.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание бренда.</b>
 *  {@link CreateBrandParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданного бренда в БД.</b>
 *  {@link CreateBrandParameterizedTest}</li>
 *      <li><b>Получение информации по бренду:</b>
 *  Получение информации о бренде по API CAP, по ручке {@code GET  /_cap/api/v1/brands/{uuid}}.
 *  В ответе есть все обязательные поля</li>
 *      <li><b>Постусловие. Удаление созданного бренда.</b> {@link DeleteBrandTest}</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/brands/{uuid}")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform") @Tag("Brand")
class GetBrandTest extends BaseTest {

    @Test
    @DisplayName("Получение бренда по его ID")
    void shouldGetBrand() {

        final class TestContext {
            CreateBrandRequest createBrandRequest;
            ResponseEntity<CreateBrandResponse> createBrandResponse;
            GetBrandRequest getBrandRequest;
            ResponseEntity<GetBrandResponse> GetBrandResponse;
            DeleteBrandRequest deleteBrandRequest;
            ResponseEntity<Void> DeleteBrandResponse;
            String createdBrandId;
        }
        final TestContext ctx = new TestContext();

        step("1. Предусловие. Создание бренда", () -> {
            ctx.createBrandRequest = CreateBrandRequest.builder()
                    .sort(1)
                    .alias(get(ALIAS, 5))
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 5)))
                    .description(get(LETTERS, 5))
                    .build();

            ctx.createBrandResponse = capAdminClient.createBrand(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createBrandRequest
            );

            assertAll(
                    "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createBrandResponse.getBody().getId())
            );

            ctx.createdBrandId = ctx.createBrandResponse.getBody().getId();

        });

        step("2. Предусловие. DB Brand: проверка создания бренда", () -> {
            var brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createdBrandId);
            assertAll("Проверка ",
                    () -> assertEquals(brand.getUuid(), ctx.createdBrandId)
            );
        });

        step("3. Получение бренда по ID", () -> {
            ctx.getBrandRequest = GetBrandRequest.builder().id(ctx.createdBrandId).build();

            ctx.GetBrandResponse = capAdminClient.getBrandId(
                    ctx.createdBrandId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertAll(
                    "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getId()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getNames()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getAlias()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getGameIds()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getStatus()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getSort()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getNodeId()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getCreatedAt()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getUpdatedAt()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getCreatedBy()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getUpdatedAt()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getIcon()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getLogo()),
                    () -> assertNotNull(ctx.GetBrandResponse.getBody().getColorLogo())
            );

        });

        step("4. Постусловие. Удаление бренда по ID", () -> {
            ctx.deleteBrandRequest = DeleteBrandRequest.builder().id(ctx.createdBrandId).build();

            ctx.DeleteBrandResponse = capAdminClient.deleteBrand(
                    ctx.createdBrandId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(
                    HttpStatus.NO_CONTENT, ctx.DeleteBrandResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });
    }
}