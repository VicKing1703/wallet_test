package com.uplatform.wallet_tests.tests.platform.brands;

import com.uplatform.wallet_tests.api.http.cap.dto.brand.CreateBrandRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.CreateBrandResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.GetBrandRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.brand.GetBrandResponse;
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
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/brand/{uuid}")
@Suite("Позитивный сценарий: Действия с брендами")
@Tag("Platform")
class GetBrandTest extends BaseTest {

    private String createdBrandId;

    @Test
    @DisplayName("Получение бренда по его ID")
    void shouldCreateCategory() {

        final class TestContext {
            CreateBrandRequest createBrandRequest;
            ResponseEntity<CreateBrandResponse> createBrandResponse;
            GetBrandRequest getBrandRequest;
            ResponseEntity<GetBrandResponse> GetBrandResponse;
        }
        final TestContext ctx = new TestContext();

        step("Создание бренда", () -> {
            ctx.createBrandRequest = CreateBrandRequest.builder()
                    .sort(1)
                    .alias(get(ALIAS))
                    .names(Map.of(LangEnum.RUSSIAN, get(BRAND_TITLE, 5)))
                    .description("Пока оставлю так")
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
            createdBrandId = ctx.createBrandResponse.getBody().getId();
        });

        step("Создан бренд с ID: " + createdBrandId);

        step("Ожидание перед удалением (для избежания deadlock)", () -> {
            Thread.sleep(2000); // пауза 2 секунды
        });

        step("Получение бренда по ID", () -> {
            ctx.getBrandRequest = GetBrandRequest.builder().id(createdBrandId).build();

            ctx.GetBrandResponse = capAdminClient.getBrandId(
                    createdBrandId,
                    utils.getAuthorizationHeader(),
                    configProvider.getEnvironmentConfig().getPlatform().getGroupId(),
                    configProvider.getEnvironmentConfig().getPlatform().getNodeId(),
                    configProvider.getEnvironmentConfig().getApi().getCapCredentials().getUsername());

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
    }
}