package com.uplatform.wallet_tests.tests.wallet.betting.bet;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
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

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.TURNOVER_LIMIT_REACHED;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет отказ в ставке при превышении лимита Turnover.
 *
 * Через Public API игроку задаётся лимит на оборот средств. Попытка
 * поставить сумму, превышающую допустимый оборот, должна завершиться
 * ошибкой TURNOVER_LIMIT_REACHED.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> установка лимита Turnover через Public API.</li>
 *   <li><b>Проверка NATS:</b> получение события limit_changed_v2.</li>
 *   <li><b>Основное действие:</b> попытка сделать ставку выше лимита.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и ошибка TURNOVER_LIMIT_REACHED.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка лимита Turnover</li>
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
class BetWithTurnoverLimitParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> couponProvider() {
        return Stream.of(
                Arguments.of(NatsBettingCouponType.SINGLE, "Ставка с купоном SINGLE"),
                Arguments.of(NatsBettingCouponType.EXPRESS, "Ставка с купоном EXPRESS"),
                Arguments.of(NatsBettingCouponType.SYSTEM, "Ставка с купоном SYSTEM")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("couponProvider")
    @DisplayName("Совершение ставки в iframe, превышающей TurnoverLimit")
    void shouldRejectBetWhenTurnoverLimitReached(NatsBettingCouponType couponType, String description) {
        final BigDecimal limitAmount = new BigDecimal("150.12");
        final BigDecimal betAmount = new BigDecimal("170.15");
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var request = SetTurnoverLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "public_api.status_code");

            step("Sub-step Kafka: получение события limit_changed_v2", () -> {
                var expectedAmount = limitAmount.stripTrailingZeros().toPlainString();
                ctx.kafkaLimitMessage = kafkaClient.expect(LimitMessage.class)
                        .with("playerId", ctx.registeredPlayer.getWalletData().playerUUID())
                        .with("limitType", NatsLimitType.TURNOVER_FUNDS.getValue())
                        .with("currencyCode", ctx.registeredPlayer.getWalletData().currency())
                        .with("amount", expectedAmount)
                        .fetch();

                assertNotNull(ctx.kafkaLimitMessage, "kafka.limit_changed_v2.message_not_found");
            });

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                ctx.limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                        .from(subject)
                        .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                        .with("$.event_type", NatsLimitEventType.CREATED.getValue())
                        .with("$.limits[0].external_id", ctx.kafkaLimitMessage.id())
                        .with("$.limits[0].limit_type", ctx.kafkaLimitMessage.limitType())
                        .with("$.limits[0].interval_type", NatsLimitIntervalType.DAILY.getValue())
                        .with("$.limits[0].amount", ctx.kafkaLimitMessage.amount())
                        .with("$.limits[0].currency_code", ctx.kafkaLimitMessage.currencyCode())
                        .with("$.limits[0].expires_at", ctx.kafkaLimitMessage.expiresAt())
                        .with("$.limits[0].status", true)
                        .fetch();

                assertNotNull(ctx.limitCreateEvent, "nats.event.limit_changed_v2");

                var limit = ctx.limitCreateEvent.getPayload().getLimits().get(0);
                assertAll("nats.limit_changed_v2_event.content_validation",
                        () -> assertEquals(NatsLimitEventType.CREATED.getValue(), ctx.limitCreateEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                        () -> assertEquals(ctx.kafkaLimitMessage.id(), limit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                        () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), limit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                        () -> assertEquals(NatsLimitIntervalType.DAILY.getValue(), limit.getIntervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                        () -> assertEquals(0, new BigDecimal(ctx.kafkaLimitMessage.amount()).compareTo(limit.getAmount()), "nats.limit_changed_v2_event.limit.amount"),
                        () -> assertEquals(ctx.kafkaLimitMessage.currencyCode(), limit.getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                        () -> assertNotNull(limit.getStartedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                        () -> assertEquals(ctx.kafkaLimitMessage.expiresAt(), limit.getExpiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                        () -> assertTrue(limit.getStatus(), "nats.limit_changed_v2_event.limit.status")
                );
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
                    () -> assertEquals(TURNOVER_LIMIT_REACHED.getDescription(), response.getBody().getDescription(), "manager_api.body.description"),
                    () -> assertEquals(TURNOVER_LIMIT_REACHED.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode")
            );
        });
    }
}