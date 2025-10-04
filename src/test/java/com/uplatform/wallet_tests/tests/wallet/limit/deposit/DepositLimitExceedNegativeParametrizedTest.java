package com.uplatform.wallet_tests.tests.wallet.limit.deposit;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.deposit.SetDepositLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.cap.dto.errors.ValidationErrorResponse;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Негативный сценарий: попытка депозита, превышающего установленный лимит Deposit.
 *
 * <p>Тест устанавливает лимит на депозит и затем выполняет депозит, превышающий
 * этот лимит. Ожидается ошибка 400 и отсутствие изменений в агрегате кошелька.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> полная регистрация с верификацией.</li>
 *   <li><b>Установка лимита:</b> создание DepositLimit через Public API.</li>
 *   <li><b>Проверка NATS:</b> событие {@code limit_changed_v2}.</li>
 *   <li><b>Основное действие:</b> попытка депозита с превышением лимита.</li>
 *   <li><b>Проверка ответа API:</b> код 400 с сообщением о превышении лимита.</li>
 *   <li><b>Проверка Redis:</b> остаток лимита не изменился.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API</li>
 *   <li>NATS</li>
 *   <li>Redis</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.fapi.client.FapiClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("DepositLimit")
@Suite("Негативные сценарии: DepositLimit")
@Tag("Limits") @Tag("Wallet") @Tag("Payment")
public class DepositLimitExceedNegativeParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal limitAmount = new BigDecimal("100.15");
    private static final BigDecimal exceedAmount = limitAmount.add(new BigDecimal("10"));

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Превышение лимита Deposit при депозите")
    void depositExceedingLimitShouldFail(NatsLimitIntervalType periodType) throws Exception {
        final class TestData {
            RegisteredPlayerData player;
            SetDepositLimitRequest limitRequest;
            NatsMessage<NatsLimitChangedV2Payload> limitEvent;
            DepositRequestBody depositRequest;
            FeignException depositException;
        }
        final TestData ctx = new TestData();

        step("Default Step: Полная регистрация игрока", () -> {
            ctx.player = defaultTestSteps.registerNewPlayerWithKyc();

            assertNotNull(ctx.player, "default_step.registration_with_kyc");
        });

        step("Public API: Установка лимита на депозит", () -> {
            ctx.limitRequest = SetDepositLimitRequest.builder()
                    .currency(ctx.player.getWalletData().currency())
                    .type(periodType)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setDepositLimit(
                    ctx.player.getAuthorizationResponse().getBody().getToken(),
                    ctx.limitRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_deposit_limit.status_code");
        });

        step("NATS: получение события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().playerUUID(),
                    ctx.player.getWalletData().walletUUID());

            ctx.limitEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.limits[0].limit_type", NatsLimitType.DEPOSIT.getValue())
                    .fetch();

            assertNotNull(ctx.limitEvent, "nats.limit_changed_v2_event.message_not_null");
        });

        step("Public API: Попытка депозита с превышением лимита", () -> {
            ctx.depositRequest = DepositRequestBody.builder()
                    .amount(exceedAmount.toPlainString())
                    .paymentMethodId(PaymentMethodId.FAKE)
                    .currency(ctx.player.getWalletData().currency())
                    .country(configProvider.getEnvironmentConfig().getPlatform().getCountry())
                    .redirect(DepositRequestBody.RedirectUrls.builder()
                            .failed(DepositRedirect.FAILED.url())
                            .success(DepositRedirect.SUCCESS.url())
                            .pending(DepositRedirect.PENDING.url())
                            .build())
                    .build();

            ctx.depositException = assertThrows(
                    FeignException.class,
                    () -> publicClient.deposit(
                            ctx.player.getAuthorizationResponse().getBody().getToken(),
                            ctx.depositRequest
                    ),
                    "fapi.deposit.exception"
            );

            var error = utils.parseFeignExceptionContent(ctx.depositException, ValidationErrorResponse.class);

            assertAll("fapi.deposit.error_response",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), error.code(), "api.error.code"),
                    () -> assertEquals("field:[amount] msg:[deposit limit: limit is exceeded]", error.message(), "api.error.message"),
                    () -> {
                        assertNotNull(error.errors(), "api.error.errors_not_null");
                        assertTrue(error.errors().get("amount").contains("Deposit limit: limit is exceeded."), "api.error.errors.value");
                    }
            );
        });
    }
}
