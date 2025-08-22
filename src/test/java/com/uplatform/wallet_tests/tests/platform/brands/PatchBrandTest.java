package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.core.CoreBrand;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.*;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.BrandCreateEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.BrandUpdateEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.enums.BrandEventType;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Этот тест проверяет API CAP на успешность изменение бренда.
 *
 * <h3> Сценарий: Успешное изменение бренда.</h3>
 * <ol>
 *      <li><b>Предусловие. Создание бренда.</b>
 *  {@link CreateBrandParameterizedTest}</li>
 *      <li><b>Предусловие. Нахождение созданной бренда в БД.</b>
 *  {@link CreateBrandParameterizedTest}</li>
 *      <li><b>Изменение данных бренда:</b>
 *  Изменение названия, алиаса и описание бренда по API CAP, по ручке {@code PATCH  /_cap/api/v1/brands/{uuid}}</li>
 *  <li><b>Поиск сообщения в Кафке о создании бренда.</b>
 *  Проверяем в топике {@code core.gambling.v1.Brand} сообщение об изменении бренда, что его uuid соответствуют,
 *  передаётся дата обновления, правильный алиас, статус, uuid ноды и названия </li>
 *      <li><b>Постусловие. Удаление созданного бренда.</b> {@link DeleteBrandTest}</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/brands/{uuid}")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform") @Tag("Brands") @Tag("UpdateBrand")
public class PatchBrandTest extends BaseTest {

    @Test
    @DisplayName("Изменение бренда")
    void shouldPatchBrand() {

        final class TestContext {
            CreateBrandRequest createBrandRequest;
            ResponseEntity<CreateBrandResponse> createBrandResponse;
            PatchBrandRequest patchBrandRequest;
            ResponseEntity<PatchBrandResponse> patchBrandResponse;
            DeleteBrandRequest deleteBrandRequest;
            ResponseEntity<Void> DeleteBrandResponse;
            String createdBrandId;
            BrandUpdateEvent brandEvent;
            CoreBrand brand;
        }
        final TestContext ctx = new TestContext();

        step("1. Предусловие.Создание бренда", () -> {
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
                    "Проверка тела ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode(),
                            "Код ответа должен быть 200 ОК"),
                    () -> assertNotNull(ctx.createBrandResponse.getBody().getId(),
                            "В теле ответа должен быть uuid созданного бренда")
            );
        });

        step("2. Предусловие. DB Brand: проверка создания бренда", () -> {
            ctx.brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createBrandResponse.getBody().getId());
            assertAll("Проверка записи в БД",
                    () -> assertEquals(ctx.brand.getUuid(), ctx.createBrandResponse.getBody().getId(),
                            "Uuid из ответа и в БД должны быть одинаковые")
            );
        });

        step("3. Изменение бренда", () -> {
            ctx.patchBrandRequest = PatchBrandRequest.builder()
                    .sort(1)
                    .alias(get(ALIAS, 11))
                    .names(Map.of(LangEnum.RUSSIAN, get(TITLE, 11)))
                    .description(get(LETTERS, 11))
                    .build();

            ctx.patchBrandResponse = capAdminClient.patchBrand(
                    ctx.createdBrandId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    ctx.patchBrandRequest
            );

            assertAll(
                    "Проверяем ответ",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode(),
                            "Ожидался код 200, но получен: " + ctx.createBrandResponse.getStatusCode()),
                    () -> assertNotNull(ctx.patchBrandResponse.getBody())
            );
        });

        step("4. Kafka: platform отправляет сообщение о создании бренда в Kafka", () -> {
            ctx.brandEvent = kafkaClient.expect(BrandUpdateEvent.class)
                    .with("message.eventType", BrandEventType.BRAND_UPDATED.getValue())
                    .with("brand.uuid", ctx.createBrandResponse.getBody().getId())
                    .fetch();

            assertAll("Проверяем сообщение в Kafka",
                    () -> assertNotNull(ctx.brandEvent, "Должно быть сообщение из Kafka"),
                    () -> assertEquals(BrandEventType.BRAND_CREATED, ctx.brandEvent.getMessage().getEventType(),
                            "Тип события в Kafka должен быть gambling.gameBrandCreated"),
                    () -> assertEquals(ctx.createBrandResponse.getBody().getId(),
                            ctx.brandEvent.getBrand().getUuid(),
                            "UUID бренда в Kafka должен совпадать с UUID из ответа"),
                    () -> assertEquals(ctx.createBrandRequest.getAlias(),
                            ctx.brandEvent.getBrand().getAlias(),
                            "Алиас в Kafka должен совпадать с алиасом из запроса"),
                    () -> assertEquals(ctx.createBrandRequest.getNames(),
                            ctx.brandEvent.getBrand().getLocalized_names(),
                            "Localized names в Kafka должны совпадать с запросом"),
                    () -> assertEquals(ctx.brand.getUpdatedAt(), ctx.brandEvent.getBrand().getUpdated_at(),
                            "Поле updated_at как и в БД"),
                    () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                            ctx.brandEvent.getBrand().getProject_id(),
                            "project_id должен соответствовать с platform-nodeid из хедера запроса"),
                    () -> assertFalse(ctx.brandEvent.getBrand().getStatus_enabled(),
                            "статус созданного бренда по умолчанию должен быть false")
            );
        });

        step("5. Постусловие. Удаление бренда по ID", () -> {
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
