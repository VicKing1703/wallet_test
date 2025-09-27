package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
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
import java.util.UUID;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий состояние лимита на оборот средств в агрегате игрока
 * после совершения ставки на спорт (betting) и последующего сообщения о возврате (refund) ставки через Manager API.
 * Тест проверяет, что значения лимита (rest, spent) соответствуют ожидаемым после этих операций.
 * При возврате ставки, потраченная сумма лимита (spent) должна уменьшиться на сумму ставки (фактически,
 * если это была единственная операция, влияющая на лимит, spent должен стать 0), а оставшаяся (rest)
 * должна увеличиться на ту же сумму.
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API: Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *   <li>REST API:
 *     <ul>
 *      <li>Совершение ставки через Manager API ({@code /make-payment} для betting, операция BET).</li>
 *      <li>Обработка возврата по ставке через Manager API ({@code /make-payment} для betting, операция REFUND).</li>
 *     </ul>
 *   </li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Передача события {@code refunded_from_iframe} через NATS при обработке возврата.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Проверка состояния лимита игрока в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>})
 *       после события возврата.</li>
 * </ul>
 *
 * <p><b>Проверяемый тип ставки ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code BET} - ставка на спорт (предшествует возврату).</li>
 *   <li>{@code REFUND} - возврат по ставке на спорт.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Betting") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenRefundFromIframeParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);
    private static final BigDecimal betAmount = generateBigDecimalAmount(limitAmountBase);
    private static final BigDecimal refundCoefficient = new BigDecimal("1.00");

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Проверка состояния TurnoverLimit после ставки и последующего возврата")
    void shouldUpdateTurnoverLimitCorrectlyAfterRefundFromIframe(NatsLimitIntervalType periodType) {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            NatsMessage<NatsBettingEventPayload> refundedEvent;
            BigDecimal expectedRestAmountAfterOperations;
            BigDecimal expectedSpentAmountAfterOperations;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedSpentAmountAfterOperations = BigDecimal.ZERO;
        ctx.expectedRestAmountAfterOperations = limitAmountBase;

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var request = SetTurnoverLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .type(periodType)
                    .amount(limitAmountBase.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                var expectedAmount = new BigDecimal(request.getAmount()).stripTrailingZeros().toPlainString();

                ctx.limitCreateEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                        .from(subject)
                        .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                        .with("$.event_type", NatsLimitEventType.CREATED.getValue())
                        .with("$.limits[0].limit_type", NatsLimitType.TURNOVER_FUNDS.getValue())
                        .with("$.limits[0].interval_type", periodType.getValue())
                        .with("$.limits[0].currency_code", request.getCurrency())
                        .with("$.limits[0].amount", expectedAmount)
                        .with("$.limits[0].status", true)
                        .fetch();

                assertNotNull(ctx.limitCreateEvent, "nats.limit_changed_v2_event");
            });
        });

        step("Manager API: Совершение ставки на спорт (BET)", () -> {
            ctx.betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.getWalletData().playerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .build();

            ctx.betRequestBody = generateRequest(ctx.betInputData);
            var response = managerClient.makePayment(ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment(BET).status_code");
        });

        step("Manager API: Получение возврата по ставке (REFUND)", () -> {
            ctx.betRequestBody.setType(NatsBettingTransactionOperation.REFUND);
            ctx.betRequestBody.setTotalCoef(refundCoefficient.toString());

            var response = managerClient.makePayment(ctx.betRequestBody);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment(REFUND).status_code");

            step("Sub-step NATS: Проверка поступления события refunded_from_iframe", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                ctx.refundedEvent = natsClient.expect(NatsBettingEventPayload.class)
                        .from(subject)
                        .withType(NatsEventType.REFUNDED_FROM_IFRAME.getHeaderValue())
                        .with("$.bet_id", ctx.betRequestBody.getBetId())
                        .with("$.type", NatsBettingTransactionOperation.REFUND.getValue())
                        .fetch();

                assertNotNull(ctx.refundedEvent, "nats.refunded_from_iframe_event");
                assertNotNull(ctx.refundedEvent.getPayload().getUuid(), "nats.refunded_from_iframe_event.payload.uuid_not_null");
                assertDoesNotThrow(() -> UUID.fromString(ctx.refundedEvent.getPayload().getUuid()),
                        "nats.refunded_from_iframe_event.payload.uuid_format");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате после всех операций", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.refundedEvent.getSequence())
                    .fetch();

            assertAll("redis.wallet.limit_data_validation",
                    () -> assertEquals((int) ctx.refundedEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertFalse(aggregate.limits().isEmpty(), "redis.wallet.limits_list_not_empty"),
                    () -> {
                        var turnoverLimitOpt = aggregate.limits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        periodType.getValue().equals(l.getIntervalType()) &&
                                        ctx.registeredPlayer.getWalletData().currency().equals(l.getCurrencyCode()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit_present");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, ctx.expectedRestAmountAfterOperations.compareTo(turnoverLimit.getRest()), "redis.wallet.limit.rest");
                        assertEquals(0, ctx.expectedSpentAmountAfterOperations.compareTo(turnoverLimit.getSpent()), "redis.wallet.limit.spent");
                        assertEquals(0, limitAmountBase.compareTo(turnoverLimit.getAmount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}
