package com.uplatform.wallet_tests.tests.wallet.betting.bet;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SetSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
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
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SINGLE_BET_LIMIT_REACHED;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет отказ в ставке при превышении лимита SingleBet.
 *
 * Игроку устанавливается лимит на размер одиночной ставки. Попытка сделать
 * ставку на сумму, превышающую этот лимит, должна завершиться ошибкой
 * SINGLE_BET_LIMIT_REACHED.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> установка лимита SingleBet через Public API.</li>
 *   <li><b>Проверка NATS:</b> получение события limit_changed_v2.</li>
 *   <li><b>Основное действие:</b> попытка сделать ставку выше лимита.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и ошибка SINGLE_BET_LIMIT_REACHED.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка лимита SingleBet</li>
 *   <li>NATS: событие limit_changed_v2</li>
 *   <li>REST API: makePayment</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Негативные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet") @Tag("Limits")
class BetWithSingleBetLimitParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> couponProvider() {
        return Stream.of(
                Arguments.of(NatsBettingCouponType.SINGLE, "Ставка с купоном SINGLE"),
                Arguments.of(NatsBettingCouponType.EXPRESS, "Ставка с купоном EXPRESS"),
                Arguments.of(NatsBettingCouponType.SYSTEM, "Ставка с купоном SYSTEM")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("couponProvider")
    @DisplayName("Совершение ставки в iframe, превышающей SingleBetLimit")
    void shouldRejectBetWhenSingleBetLimitReached(NatsBettingCouponType couponType, String description) {
        final BigDecimal limitAmount = new BigDecimal("150.12");
        final BigDecimal betAmount = new BigDecimal("170.15");
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на одиночную ставку", () -> {
            var request = SetSingleBetLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .amount(limitAmount.toString())
                    .build();

            var response = publicClient.setSingleBetLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "public_api.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                        NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader);

                var limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .with(filter)
                    .fetch();
                assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
            });
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            var data = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.getWalletData().playerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(couponType)
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .build();

            var request = generateRequest(data);
            var response = managerClient.makePayment(request);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertFalse(response.getBody().isSuccess(), "manager_api.body.success"),
                    () -> assertEquals(SINGLE_BET_LIMIT_REACHED.getDescription(), response.getBody().getDescription(), "manager_api.body.description"),
                    () -> assertEquals(SINGLE_BET_LIMIT_REACHED.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode")
            );
        });
    }
}