package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.api.db.entity.core.CoreBrand;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.*;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.BrandEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.enums.BrandEventType;
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
 *  Успешное удаление созданного бренда по API CAP, по ручке {@code DELETE /_cap/api/v1/brands/{uuid}},
 *  по uuid из ответа на создание. В ответ получаем код 204</li>
 *      <li><b>Проверка удаления бренда в БД.</b>
 *  Проверка, что в БД {@code `_core`.brand} бренд, с uuid из создания бренда, остался,
 *  но в поле <b>deleted_at</b> не пустое</li>
 *      <li><b>Поиск сообщения в Кафке об удалении бренда.</b>
 *  Проверяем в топике {@code core.gambling.v1.Brand} сообщение об удалении бренда, что его uuid соответствуют,
 *  передаётся дата удаления</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/brands/{uuid}")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform") @Tag("Brand") @Tag("DeleteBrand")
class DeleteBrandTest extends BaseTest {

    @Test
    @DisplayName("Удаление бренда по его ID")
    void shouldDeleteBrand() {

        final class TestContext {
            CreateBrandRequest createBrandRequest;
            ResponseEntity<CreateBrandResponse> createBrandResponse;
            DeleteBrandRequest deleteBrandRequest;
            ResponseEntity<Void> DeleteBrandResponse;
            BrandEvent brandEvent;
            CoreBrand brand;

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
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode(),
                            "Ожидался код 200, но получен: " + ctx.createBrandResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createBrandResponse.getBody().getId(),
                            "ID бренда не должен быть NULL"
                    )
            );
        });

        step("2. Предусловие. DB Brand: проверка создания бренда", () -> {
            ctx.brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createBrandResponse.getBody().getId());
            assertAll("Проверка записи в БД",
                    () -> assertEquals(ctx.brand.getUuid(), ctx.createBrandResponse.getBody().getId(),
                            "Uuid из ответа и в БД должны быть одинаковые")
            );
        });

        step("3. Удаление бренда по ID", () -> {
            ctx.deleteBrandRequest = DeleteBrandRequest.builder().id(ctx.createBrandResponse.getBody().getId()).build();

            ctx.DeleteBrandResponse = capAdminClient.deleteBrand(
                    ctx.createBrandResponse.getBody().getId(),
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(HttpStatus.NO_CONTENT, ctx.DeleteBrandResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });

        step("4. DB Brand: проверка удаления бренда", () -> {
            ctx.brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createBrandResponse.getBody().getId());
            assertAll("Проверка что в БД не осталось бренда с uuid создания",
                    () -> assertEquals(ctx.brand.getUuid(), ctx.createBrandResponse.getBody().getId(), "Ожидаем бренд в БД"),
                    () -> assertNotNull(ctx.brand.getDeletedAt(), "Ожидаем дату удаления в поле deleted_at")
            );
        });

        step("5. Kafka: platform отправляет сообщение об удалении бренда в Kafka", () -> {
            ctx.brandEvent = kafkaClient.expect(BrandEvent.class)
                    .with("message.eventType", BrandEventType.BRAND_DELETED.getValue())
                    .with("brand.uuid", ctx.createBrandResponse.getBody().getId())
                    .fetch();

            assertAll("Проверяем сообщение в Kafka",
                    () -> assertNotNull(ctx.brandEvent, "Должно быть сообщение из Kafka"),
                    () -> assertEquals(BrandEventType.BRAND_DELETED, ctx.brandEvent.getMessage().getEventType(),
                            "Тип события в Kafka должен быть gambling.gameBrandDeleted"),
                    () -> assertEquals(ctx.createBrandResponse.getBody().getId(),
                            ctx.brandEvent.getBrand().getUuid(),
                            "UUID бренда в Kafka должен совпадать с UUID из ответа"),
                    () -> assertEquals(ctx.brand.getDeletedAt(), ctx.brandEvent.getBrand().getDeletedAt(),
                            "Поле created_at как и в БД")
            );
        });
    }
}
