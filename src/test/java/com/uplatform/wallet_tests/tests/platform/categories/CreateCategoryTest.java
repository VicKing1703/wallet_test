package com.uplatform.wallet_tests.tests.platform;
import com.uplatform.wallet_tests.api.http.cap.dto.category.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.category.CreateCategoryResponse;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import java.util.Map;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/categories")
@Suite("Создание категории")
@Tag("Platform")
class CreateCategoryTest extends BaseTest {

    @Test
    @DisplayName("Получение выигрыша в турнире игроком в игровой сессии")
    void shouldAwardTournamentWinAndVerify() {

        final class TestContext {
            CreateCategoryRequest createCategoryRequest;
            ResponseEntity<CreateCategoryResponse> createCategoryResponse;
        }
        final TestContext ctx = new TestContext();

        step("Создание категории", () -> {
            ctx.createCategoryRequest = CreateCategoryRequest.builder()
                    .alias("qwerty")
                    .type("vertical")
                    .sort(1)
                    .groupId("4c59ecfb-9571-4d2e-8e8b-4558636049fc")
                    .projectId("068f20a5-7c0a-4226-bf5e-6648c735a12b")
                    .names(Map.of("ru", "йцукен"))
                    .build();

            ctx.createCategoryResponse = capAdminClient.createCategory(
                    utils.getAuthorizationHeader(),
                    "068f20a5-7c0a-4226-bf5e-6648c735a12b",
                    "6dfe249e-e967-477b-8a42-83efe85c7c3a",
                    "admin",
                    ctx.createCategoryRequest);

            assertAll(
              "Проверяю тело ответа",
                    () -> assertEquals(HttpStatus.OK, ctx.createCategoryResponse.getStatusCode()),
                    () -> assertNotNull(ctx.createCategoryResponse.getBody().getId())
            );
        });
    }
}