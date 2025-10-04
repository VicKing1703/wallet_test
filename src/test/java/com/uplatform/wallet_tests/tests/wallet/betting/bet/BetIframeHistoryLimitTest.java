package com.uplatform.wallet_tests.tests.wallet.betting.bet;

import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Проверяет ограничение числа записей ставок из iframe в Redis.
 *
 * Тест совершает серию ставок новым игроком, чтобы превысить лимит хранимых
 * записей iFrame, и убеждается, что старые записи корректно удаляются, а
 * баланс кошелька изменяется ожидаемым образом.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание пользователя с балансом.</li>
 *   <li><b>Основное действие:</b> последовательное совершение ставок через
 *   makePayment.</li>
 *   <li><b>Проверка ответа API:</b> для каждой ставки статус 200 и успешное
 *   тело.</li>
 *   <li><b>Проверка NATS:</b> получение события betted_from_iframe для
 *   последней ставки.</li>
 *   <li><b>Проверка Redis:</b> количество записей iFrame не превышает лимит и
 *   баланс уменьшен корректно.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 *   <li>NATS: событие betted_from_iframe</li>
 *   <li>Redis кошелька</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Позитивные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
class BetIframeHistoryLimitTest extends BaseTest {

    private static final BigDecimal singleBetAmount = new BigDecimal("1.00");
    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("550.00");

    @Test
    @DisplayName("Проверка лимита количества iFrame ставок (IFrameRecords) в Redis")
    void testIframeBetHistoryCountLimitInRedis() {
        final int maxIframeCountInRedis = configProvider.getEnvironmentConfig().getRedis().getAggregate().maxIframeCount();
        final int currentTransactionCountToMake = maxIframeCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            Long lastBetId;
            MakePaymentRequest lastBetRequest;
            NatsMessage<NatsBettingEventPayload> lastBetEvent;
            BigDecimal currentBalance;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            ctx.currentBalance = ctx.registeredPlayer.getWalletData().balance();

            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Совершение iFrame ставок", () -> {
            for (int i = 0; i < currentTransactionCountToMake; i++) {
                var betInputData = MakePaymentData.builder()
                        .type(NatsBettingTransactionOperation.BET)
                        .playerId(ctx.registeredPlayer.getWalletData().playerUUID())
                        .summ(singleBetAmount.toPlainString())
                        .couponType(NatsBettingCouponType.SINGLE)
                        .currency(ctx.registeredPlayer.getWalletData().currency())
                        .build();

                var betRequestBody = MakePaymentRequestGenerator.generateRequest(betInputData);

                if (i == currentTransactionCountToMake - 1) {
                    ctx.lastBetId = betRequestBody.getBetId();
                    ctx.lastBetRequest = betRequestBody;
                }

                var currentBetNumber = i + 1;
                var currentBetIdForStep = betRequestBody.getBetId();

                step("Совершение iFrame ставки #" + currentBetNumber + " с BetID: " + currentBetIdForStep, () -> {
                    var response = managerClient.makePayment(betRequestBody);

                    assertAll("Проверка ответа API для iFrame ставки #" + currentBetNumber,
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                            () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode"),
                            () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.body.description")
                    );

                    ctx.currentBalance = ctx.currentBalance.subtract(singleBetAmount);
                });
            }
        });

        step("NATS: Ожидание NATS-события betted_from_iframe для последней ставки", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().playerUUID(),
                    ctx.registeredPlayer.getWalletData().walletUUID());

            ctx.lastBetEvent = natsClient.expect(NatsBettingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_IFRAME.getHeaderValue())
                    .with("$.bet_id", ctx.lastBetId)
                    .fetch();

            assertNotNull(ctx.lastBetEvent, "nats.betted_from_iframe");
        });

        step("Redis(Wallet): Получение и проверка данных кошелька (лимит iFrame ставок)", () -> {
            step("Redis(Wallet): Получение и проверка данных кошелька (лимит iFrame ставок)", () -> {
                var aggregate = redisWalletClient
                        .key(ctx.registeredPlayer.getWalletData().walletUUID())
                        .withAtLeast("LastSeqNumber", (int) ctx.lastBetEvent.getSequence())
                        .fetch();

                var iFrameRecordsInRedis = aggregate.iFrameRecords();

                assertAll("Проверка данных iFrame в Redis",
                        () -> assertEquals(maxIframeCountInRedis - 1, iFrameRecordsInRedis.size(),"redis.wallet.gambling.count"),
                        () -> assertEquals(0, ctx.currentBalance.compareTo(aggregate.balance()),"redis.wallet.balance")
                );
            });
        });
    }
}
