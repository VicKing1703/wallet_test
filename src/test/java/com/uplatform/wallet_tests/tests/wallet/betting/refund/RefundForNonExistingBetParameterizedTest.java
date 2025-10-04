package com.uplatform.wallet_tests.tests.wallet.betting.refund;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.NOT_FOUND;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Отказ в refund по несуществующей ставке.
 *
 * Сервис должен вернуть NOT_FOUND при refund с случайным betId.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> новый пользователь.</li>
 *   <li><b>Основное действие:</b> запрос refund с несуществующим betId.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и NOT_FOUND.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Негативные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
class RefundForNonExistingBetParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> couponProvider() {
        return Stream.of(
                Arguments.of(NatsBettingCouponType.SINGLE, "Рефанд несуществующей ставки с купоном SINGLE"),
                Arguments.of(NatsBettingCouponType.EXPRESS, "Рефанд несуществующей ставки с купоном EXPRESS"),
                Arguments.of(NatsBettingCouponType.SYSTEM, "Рефанд несуществующей ставки с купоном SYSTEM")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("couponProvider")
    @DisplayName("Попытка зарегистрировать рефанд для несуществующей ставки iframe")
    void shouldRejectRefundForNonExistingBet(NatsBettingCouponType couponType, String description) {
        final BigDecimal adjustmentAmount = new BigDecimal("100.00");
        final BigDecimal refundSumForRequest = generateBigDecimalAmount(adjustmentAmount);
        final String refundCoefficientForRequest = "1.00";
        final Long nonExistingBetId = System.currentTimeMillis();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Попытка зарегистрировать рефанд для несуществующей ставки", () -> {
            var refundInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.REFUND)
                    .playerId(ctx.registeredPlayer.getWalletData().playerUUID())
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .summ(refundSumForRequest.toPlainString())
                    .totalCoef(refundCoefficientForRequest)
                    .couponType(couponType)
                    .betId(nonExistingBetId)
                    .build();

            var refundRequestBody = generateRequest(refundInputData);
            var response = managerClient.makePayment(refundRequestBody);

            assertAll("Проверка статус-кода и тела ответа при попытке рефанда для несуществующей ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund_non_existing.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.refund_non_existing.body_is_null"),
                    () -> assertFalse(response.getBody().success(), "manager_api.refund_non_existing.body.success"),
                    () -> assertEquals(NOT_FOUND.getDescription(), response.getBody().description(), "manager_api.refund_non_existing.body.description"),
                    () -> assertEquals(NOT_FOUND.getCode(), response.getBody().errorCode(), "manager_api.refund_non_existing.body.errorCode")
            );
        });
    }
}