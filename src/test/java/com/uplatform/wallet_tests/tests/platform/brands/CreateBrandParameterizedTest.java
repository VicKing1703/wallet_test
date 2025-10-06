package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.api.db.entity.core.CoreBrand;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.CreateBrandRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.CreateBrandResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.DeleteBrandRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.BrandEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v1.brand.enums.BrandEventType;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.GameBrandEvent;
import com.uplatform.wallet_tests.api.kafka.dto.core.gambling.v3.game.enums.GameEventType;
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

/**
 * Этот параметризованный тест проверяет API CAP на успешность создания брендов
 * с разной длинной названия, алиаса и описания.
 *
 * <h3> Сценарий: Успешное создание бренда.</h3>
 * <ol>
 *      <li><b>Создание нового бренда:</b>
 *  Создание нового бренда через API CAP, по ручке {@code POST /_cap/api/v1/brands}.
 *  В ответе код 200 и уникальный uuid созданного бренда</li>
 *      <li><b>Нахождение созданного бренда в БД:</b>
 *  <li>В БД {@code `_core`.brand} есть запись с uuid из ответа на создание бренда</li>
 *      <li><b>Поиск сообщения в Кафке о создании бренда.</b>
 *  <p>Проверяем в топике {@code core.gambling.v1.Brand} сообщение о создании бренда, что его uuid соответствуют,
 *  передаётся дата создания, правильный алиас, статус, uuid ноды и названия <p>
 *  <p>Проверяем в топике {@code core.gambling.v3.Game} сообщение о создании бренда, что его uuid соответствуют,
 *  передаётся дата создания, правильный алиас, статус, uuid ноды и названия<p></li>
 *      <li><b>Постусловие: удаление созданного бренда.</b> {@link DeleteBrandTest}</li>
 * </ol>
 */

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/brands")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform") @Tag("Brand") @Tag("CreateBrand")
class CreateBrandParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> brandParams() {
        return Stream.of(
                Arguments.of(25, 0, 2, "длинны строк: название 25, описание 0, алиас 2"),
                Arguments.of(2, 50, 100, "длинны строк: название 2, описание 50, алиас 100")
        );
    }

    @ParameterizedTest(name = ": длина названия = {0}, описания = {1} и алиаса = {2}")
    @MethodSource("brandParams")
    @DisplayName("Создание бренда")
    void shouldCreateBrand(Integer titleLengths, Integer descriptionLengths, Integer aliasLengths) {
        final class TestContext {
            CreateBrandRequest createBrandRequest;
            ResponseEntity<CreateBrandResponse> createBrandResponse;

            DeleteBrandRequest deleteBrandRequest;
            ResponseEntity<Void> deleteBrandResponse;

            CoreBrand brand;

            BrandEvent brandEvent;
            GameBrandEvent gameBrandEvent;
        };

        final TestContext ctx = new TestContext();

        step("1. Создание нового бренда", () -> {
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
                            "Ожидался код 200, но получен: " + ctx.createBrandResponse.getStatusCode()
                    ),
                    () -> assertNotNull(ctx.createBrandResponse.getBody().getId(),
                            "ID категории не должен быть null"
                    )
            );
        });

        step("2. DB Brand: проверка создания бренда", () -> {
            ctx.brand = coreDatabaseClient.findBrandByUuidOrFail(
                    ctx.createBrandResponse.getBody().getId()
            );

            assertAll("Проверка записи в БД",
                    () -> assertEquals(ctx.brand.getUuid(), ctx.createBrandResponse.getBody().getId(),
                            "Uuid из ответа и в БД должны быть одинаковые")
            );
        });

        step("3. Kafka: platform отправляет сообщение о создании бренда в Kafka", () -> {
                    step("Топик _core.gambling.v1.Brand", () -> {
                        ctx.brandEvent = kafkaClient.expect(BrandEvent.class)
                                .with("message.eventType", BrandEventType.BRAND_CREATED.getValue())
                                .with("brand.uuid", ctx.createBrandResponse.getBody().getId())
                                .fetch();

                        assertAll("Проверяем сообщение в Kafka",
                                () -> assertNotNull(ctx.brandEvent, "Должно быть сообщение в топике"),
                                () -> assertEquals(BrandEventType.BRAND_CREATED, ctx.brandEvent.getMessage().getEventType(),
                                        "Тип события в Kafka должен быть gambling.gameBrandCreated"),
                                () -> assertEquals(ctx.createBrandResponse.getBody().getId(),
                                        ctx.brandEvent.getBrand().getUuid(),
                                        "UUID бренда в Kafka должен совпадать с UUID из ответа"),
                                () -> assertEquals(ctx.createBrandRequest.getAlias(),
                                        ctx.brandEvent.getBrand().getAlias(),
                                        "Алиас в Kafka должен совпадать с алиасом из запроса"),
                                () -> assertEquals(ctx.createBrandRequest.getNames(),
                                        ctx.brandEvent.getBrand().getLocalizedNames(),
                                        "Localized names в Kafka должны совпадать с запросом"),
                                () -> assertEquals(ctx.brand.getCreatedAt(), ctx.brandEvent.getBrand().getCreatedAt(),
                                        "Поле created_at как и в БД"),
                                () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                                        ctx.brandEvent.getBrand().getProjectId(),
                                        "project_id должен соответствовать с platform-nodeid из хедера запроса"),
                                () -> assertFalse(ctx.brandEvent.getBrand().getStatusEnabled(),
                                        "статус созданного бренда по умолчанию должен быть false")
                        );
                    });

                    step("Топик _core.gambling.v3.Game", () -> {
                        ctx.gameBrandEvent = kafkaClient.expect(GameBrandEvent.class)
                                .with("message.eventType", GameEventType.BRAND.getValue())
                                .with("brand.uuid", ctx.createBrandResponse.getBody().getId())
                                .fetch();

                        assertAll("Проверяем сообщение в Kafka",
                                () -> assertNotNull(ctx.gameBrandEvent, "Должно быть сообщение в топике"),
                                () -> assertEquals(GameEventType.BRAND, ctx.gameBrandEvent.getMessage().getEventType(),
                                        "Тип события в Kafka должен быть " + GameEventType.BRAND),
                                () -> assertEquals(ctx.createBrandResponse.getBody().getId(),
                                        ctx.gameBrandEvent.getBrand().getUuid(),
                                        "UUID бренда в Kafka должен совпадать с UUID из ответа"),
                                () -> assertEquals(ctx.createBrandRequest.getAlias(),
                                        ctx.gameBrandEvent.getBrand().getAlias(),
                                        "Алиас в Kafka должен совпадать с алиасом из запроса"),
                                () -> assertEquals(ctx.createBrandRequest.getNames(),
                                        ctx.gameBrandEvent.getBrand().getLocalizedNames(),
                                        "Localized names в Kafka должны совпадать с запросом"),
                                () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                                        ctx.gameBrandEvent.getBrand().getProjectId(),
                                        "project_id должен соответствовать с platform-nodeid из хедера запроса"),
                                () -> assertEquals("disabled", ctx.gameBrandEvent.getBrand().getStatus(),
                                        "статус созданного бренда по умолчанию должен быть disabled")
                        );
                    });
        });

        step("5. Постусловие: Удаление бренда по ID", () -> {
            ctx.deleteBrandRequest = DeleteBrandRequest.builder()
                    .id(ctx.createBrandResponse.getBody().getId())
                    .build();

            ctx.deleteBrandResponse = capAdminClient.deleteBrand(
                    ctx.createBrandResponse.getBody().getId(),
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertEquals(HttpStatus.NO_CONTENT, ctx.deleteBrandResponse.getStatusCode(),
                    "Ожидаем статус 204 No Content"
            );
        });
    };
}