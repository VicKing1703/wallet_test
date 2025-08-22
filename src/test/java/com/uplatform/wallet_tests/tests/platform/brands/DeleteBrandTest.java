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

/**
 * Этот тест проверяет API CAP на успешность удаления бренда.
 *
 * <h3> Сценарий: Успешное удаление бренда.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание нового бренда.</b>
 *  {@link CreateBrandParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданного бренда в БД.</b>
 *  {@link CreateBrandParameterizedTest}</li>
 *      <li><b>Удаление созданного бренда.</b>
 *  Успешное удаление созданнго бренда по API CAP, по ручке {@code DELETE /_cap/api/v1/brands/{uuid}},
 *  по uuid из ответа на создание. В ответ получаем код 204</li>
 *      <li><b>Проверка удаления бренда в БД.</b>
 *  Проверка, что в БД {@code `_core`.brand} бренд, с uuid из создания бренда, остался,
 *  но в поле <b>deleted_at</b> не пустое</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/brands/{uuid}")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform") @Tag("Brand")
class DeleteBrandTest extends BaseTest {

    @Test
    @DisplayName("Удаление бренда по его ID")
    void shouldDeleteBrand() {

        final class TestContext {
            CreateBrandRequest createBrandRequest;
            ResponseEntity<CreateBrandResponse> createBrandResponse;
            DeleteBrandRequest deleteBrandRequest;
            ResponseEntity<Void> DeleteBrandResponse;
            String createdBrandId;
        }
        final TestContext ctx = new TestContext();

        step("1. Предусловие. Создание бренда", () -> {
            ctx.createBrandRequest = CreateBrandRequest.builder()
                    .sort(1)
                    .alias(get(ALIAS, 3))
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 3)))
                    .description(get(LETTERS, 3))
                    .build();

            ctx.createBrandResponse = capAdminClient.createBrand(
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.createBrandRequest
            );

            assertAll(
                    "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createBrandResponse.getBody().getId(),
                            "ID бренда не должен быть NULL"
                    )
            );

            ctx.createdBrandId = ctx.createBrandResponse.getBody().getId();

        });

        step("2. Предусловие. DB Brand: проверка создания бренда", () -> {
            var brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createdBrandId);
            assertAll("Проверка ",
                    () -> assertEquals(brand.getUuid(), ctx.createdBrandId)
            );
        });

        step("3. Удаление бренда по ID", () -> {
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

        step("4. DB Brand: проверка удаления бренда", () -> {
            var brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createdBrandId);
            assertAll("Проверка что в БД не осталось бренда с uuid создания",
                    () -> assertEquals(brand.getUuid(), ctx.createdBrandId, "Ожидаем бренд в БД"),
                    () -> assertNotNull(brand.getDeletedAt(), "Ожидаем дату удаления в поле deleted_at")
            );
        });
    }
}
