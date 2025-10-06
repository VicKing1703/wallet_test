package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.api.db.entity.core.CoreBrand;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.*;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.allure.Suite;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
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
@Tag("Platform") @Tag("Brand") @Tag("GetBrand")
class GetBrandTest extends BaseTest {

    private static final class TestContext {
        CreateBrandRequest createBrandRequest;
        ResponseEntity<CreateBrandResponse> createBrandResponse;

        GetBrandRequest getBrandRequest;
        ResponseEntity<GetBrandResponse> getBrandResponse;

        DeleteBrandRequest deleteBrandRequest;
        ResponseEntity<Void> deleteBrandResponse;

        CoreBrand brand;
    };

    private final TestContext ctx = new TestContext();

    @BeforeEach
    void setUp() {

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

            assertAll("Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode(),
                            "Код ответа должен быть 200 ОК, пришел" + ctx.createBrandResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createBrandResponse.getBody().getId(),
                            "В ответе должен быть uuid созданного бренда")
            );
        });

        step("2. Предусловие. DB Brand: проверка создания бренда", () -> {
            ctx.brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createBrandResponse.getBody().getId());

            assertAll("Проверка записи в БД",
                    () -> assertEquals(ctx.brand.getUuid(), ctx.createBrandResponse.getBody().getId(),
                            "Uuid из ответа и в БД должны быть одинаковые")
            );
        });
    };

    @Test
    @DisplayName("Получение бренда по его ID")
    void shouldGetBrand() {

        step("3. Получение бренда по ID", () -> {
            ctx.getBrandRequest = GetBrandRequest
                    .builder()
                    .id(ctx.createBrandResponse.getBody().getId())
                    .build();

            ctx.getBrandResponse = capAdminClient.getBrandId(
                    ctx.createBrandResponse.getBody().getId(),
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId()
            );

            assertAll("Проверяем код и тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode(),
                            "Ожидаем статус 200 ОК"),
                    () -> assertEquals(ctx.createBrandResponse.getBody().getId(), ctx.getBrandResponse.getBody().getId(),
                            "uuid при создании и из получения должны быть одинаковые"),
                    () -> assertEquals(ctx.createBrandRequest.getNames().get(LangEnum.RUSSIAN),
                            ctx.getBrandResponse.getBody().getNames().get("ru"),
                            "names при создании и из получения должны быть одинаковые"),
                    () -> assertEquals(ctx.createBrandRequest.getAlias(), ctx.getBrandResponse.getBody().getAlias(),
                            "alias при создании и из получения должны быть одинаковые"),
                    () -> assertEquals(ctx.createBrandRequest.getDescription(),
                            ctx.getBrandResponse.getBody().getDescription(),
                            "description при создании и из получения должны быть одинаковые"),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getGameIds()),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getStatus()),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getSort()),
                    () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                            ctx.getBrandResponse.getBody().getNodeId(),
                            "uuid ноды должен быть такой-же как при создании из хедера platform-nodeid"),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getCreatedAt()),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getUpdatedAt()),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getCreatedBy()),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getUpdatedAt()),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getIcon()),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getLogo()),
                    () -> assertNotNull(ctx.getBrandResponse.getBody().getColorLogo())
            );
        });
    };

    @AfterEach
    void tearDown() {

        step("4. Постусловие. Удаление бренда по ID", () -> {
            ctx.deleteBrandRequest = DeleteBrandRequest
                    .builder()
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