package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Betting") @Tag("Wallet") @Tag("Limits")
class CasinoLossLimitWhenWinFromIframeParametrizedTest extends BaseParameterizedTest {

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Изменение остатка CasinoLossLimit при получении выигрыша от iframe")
    void shouldRejectBetWhenGamblingDisabled(NatsLimitIntervalType periodType) {
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal limitAmount = new BigDecimal("150.12");
        final BigDecimal betAmount = new BigDecimal("10.15");
        final BigDecimal winAmount = new BigDecimal("20.77");

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsBettingEventPayload> winEvent;
            BigDecimal expectedRest;
            BigDecimal expectedSpent;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedSpent = betAmount.subtract(winAmount);
        ctx.expectedRest = limitAmount.subtract(ctx.expectedSpent);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            var request = SetCasinoLossLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .type(periodType)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
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
            ctx.betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.getWalletData().playerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(ctx.registeredPlayer.getWalletData().currency())
                    .build();

            ctx.betRequestBody = generateRequest(ctx.betInputData);

            var response = managerClient.makePayment(ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");
        });

        step("Manager API: Получение выигрыша", () -> {
            ctx.betRequestBody.setSumm(winAmount.toString());
            ctx.betRequestBody.setType(NatsBettingTransactionOperation.WIN);

            var response = managerClient.makePayment(ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");

            step("NATS: Проверка поступления события won_from_iframe", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().playerUUID(),
                        ctx.registeredPlayer.getWalletData().walletUUID());

                BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.WON_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                                ctx.betRequestBody.getBetId() == payload.getBetId();

                ctx.winEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .with(filter)
                    .fetch();

                assertNotNull(ctx.winEvent, "nats.event.won_from_iframe");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.winEvent.getSequence())
                    .fetch();

            var limit = aggregate.limits().get(0);
            assertAll(
                    () -> assertEquals(0, ctx.expectedRest.compareTo(limit.getRest()), "redis.aggregate.limit.rest"),
                    () -> assertEquals(0, ctx.expectedSpent.compareTo(limit.getSpent()), "redis.aggregate.limit.spent"),
                    () -> assertEquals(0, limitAmount.compareTo(limit.getAmount()), "redis.aggregate.limit.amount")
            );
        });
    }
}
