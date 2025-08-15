package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.*;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
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

@Severity(SeverityLevel.CRITICAL)
@Epic("Platform")
@Feature("/brand/{uuid}")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform") @Tag("Brands")
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
        }
        final TestContext ctx = new TestContext();

        step("Создание бренда", () -> {
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

        step("DB Brand: проверка создания бренда", () -> {
            var brand = coreDatabaseClient.findBrandByUuidOrFail(ctx.createdBrandId);
            assertAll("Проверка ",
                    () -> assertEquals(brand.getUuid(), ctx.createdBrandId)
            );
        });

        step("Изменение бренда", () -> {
            ctx.patchBrandRequest = PatchBrandRequest.builder()
                    .sort(11)
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
                    "Проверяем тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createBrandResponse.getStatusCode()),
                    () -> assertNotNull(ctx.patchBrandResponse.getBody())
            );
        });

        //временный костыль
        step("Ожидание (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунды
        });

        step("Постусловие: Удаление бренда по ID", () -> {
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
