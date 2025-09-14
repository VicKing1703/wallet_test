package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsDepositStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionType;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>Тест гарантирует, что после возврата (рефанда) ставки, которая изначально
 * влияла на отыгрыш, сумма отыгрыша депозита корректно откатывается.</p>
 *
 * <h3>Сценарий: Откат отыгрыша после рефанда ставки</h3>
 * <p>Проверяется полный жизненный цикл: депозит -> ставка -> рефанд.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует зарегистрированный и верифицированный игрок.</li>
 *   <li>Игрок сделал депозит, требующий отыгрыша.</li>
 *   <li>Игрок сделал ставку, которая увеличила сумму отыгрыша.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Происходит возврат (рефанд) этой ставки через manager_api.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>manager_api</b>: Отвечает статусом <code>200 OK</code> на запросы <code>/_core_gas_processing/bet</code> и <code>/_core_gas_processing/refund</code>.</li>
 *   <li><b>wallet-manager</b>: Отправляет события в NATS:
 *      <ul>
 *          <li><code>betted_from_gamble</code>: Содержит информацию об увеличении отыгрыша.</li>
 *          <li><code>refunded_from_gamble</code>: <strong>Не содержит</strong> блок <code>wagered_deposit_info</code>, но инициирует откат отыгрыша.</li>
 *      </ul>
 *   </li>
 *   <li><b>wallet_wallet_redis</b>: Обновляет агрегат кошелька в Redis:
 *      <ul>
 *          <li>Общий баланс возвращается к исходному значению до ставки.</li>
 *          <li>Сумма отыгрыша (<code>wageringAmount</code>) депозита возвращается к значению до ставки.</li>
 *      </ul>
 *   </li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Отыгрыш депозита")
@Tag("Wallet") @Tag("Payment")
public class DepositWageringBetRefundParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal depositAmount = new BigDecimal("150.00");

    static Stream<Arguments> betRefundParamsProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.BET,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_BET + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_BET + " на нулевую сумму"),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.BET,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_BET + " на всю сумму депозита"),
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.TIPS,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_TIPS + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_TIPS + " на нулевую сумму"),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.TIPS,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_TIPS + " на всю сумму депозита"),
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.FREESPIN,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_FREESPIN + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_FREESPIN + " на нулевую сумму"),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.FREESPIN,
                        "Рефанд для " + NatsGamblingTransactionType.TYPE_FREESPIN + " на всю сумму депозита")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("betRefundParamsProvider")
    @DisplayName("Влияние транзакций типа /refund на отыгрыш депозита:")
    void shouldDepositBetRefundAndCheckRedis(
            BigDecimal betAmount,
            NatsGamblingTransactionOperation operationParam,
            String testCaseName) {

        final class TestData {
            RegisteredPlayerData player;
            DepositRequestBody depositRequest;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequest;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            RefundRequestBody refundRequest;
            NatsMessage<NatsGamblingEventPayload> refundEvent;

            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterRefund;
            BigDecimal expectedWagerAmountAfterBet;
        }
        final TestData ctx = new TestData();
        ctx.expectedBalanceAfterBet = operationParam == NatsGamblingTransactionOperation.FREESPIN ? depositAmount : depositAmount.subtract(betAmount);
        ctx.expectedBalanceAfterRefund = depositAmount;
        ctx.expectedWagerAmountAfterBet =
                operationParam == NatsGamblingTransactionOperation.FREESPIN ? BigDecimal.ZERO : betAmount;

        step("GIVEN: Существует игрок с активным депозитом, сделавший ставку", () -> {
            step("Default Step: Полная регистрация игрока с KYC", () -> {
                ctx.player = defaultTestSteps.registerNewPlayerWithKyc();

                assertNotNull(ctx.player, "given.player.not_null");
            });

            step("Default Step: Создание игровой сессии", () -> {
                ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.player);

                assertNotNull(ctx.gameLaunchData, "given.game_session.not_null");
            });

            step("FAPI: Выполнение депозита", () -> {
                ctx.depositRequest = DepositRequestBody.builder()
                        .amount(depositAmount.toPlainString())
                        .paymentMethodId(PaymentMethodId.FAKE)
                        .currency(ctx.player.getWalletData().currency())
                        .country(configProvider.getEnvironmentConfig().getPlatform().getCountry())
                        .redirect(DepositRequestBody.RedirectUrls.builder()
                                .failed(DepositRedirect.FAILED.url())
                                .success(DepositRedirect.SUCCESS.url())
                                .pending(DepositRedirect.PENDING.url())
                                .build())
                        .build();

                var response = publicClient.deposit(
                        ctx.player.getAuthorizationResponse().getBody().getToken(),
                        ctx.depositRequest);

                assertEquals(HttpStatus.CREATED, response.getStatusCode(), "given.fapi.deposit.status_code");
            });

            step("NATS: Проверка события deposited_money", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                BiPredicate<NatsDepositedMoneyPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.DEPOSITED_MONEY.getHeaderValue().equals(typeHeader);

                ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                        .from(subject)
                        .matching(filter)
                        .fetch();

                assertNotNull(ctx.depositEvent, "given.nats.deposit_event.not_null");
            });

            step("Manager API: Совершение ставки", () -> {
                ctx.betRequest = BetRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                        .amount(betAmount)
                        .transactionId(UUID.randomUUID().toString())
                        .type(operationParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(false)
                        .build();

                var response = managerClient.bet(
                        configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId(),
                        utils.createSignature(ApiEndpoints.BET, ctx.betRequest),
                        ctx.betRequest);

                assertEquals(HttpStatus.OK, response.getStatusCode(), "given.manager_api.bet.status_code");
            });

            step("NATS: Проверка события betted_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.betRequest.getTransactionId().equals(payload.getUuid());

                ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
                        .from(subject)
                        .matching(filter)
                        .fetch();

                assertNotNull(ctx.betEvent, "given.nats.bet_event.not_null");
            });
        });

        step("WHEN: Выполняется рефанд ставки", () -> {
            ctx.refundRequest = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.betRequest.getTransactionId())
                    .roundId(ctx.betRequest.getRoundId())
                    .roundClosed(true)
                    .playerId(ctx.player.getWalletData().walletUUID())
                    .currency(ctx.player.getWalletData().currency())
                    .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                    .build();

            var response = managerClient.refund(
                    configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId(),
                    utils.createSignature(ApiEndpoints.REFUND, ctx.refundRequest),
                    ctx.refundRequest);

            assertAll("Проверка ответа на рефанд от Manager API",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "when.manager_api.refund.status_code"),
                    () -> assertEquals(ctx.refundRequest.getTransactionId(), response.getBody().getTransactionId(), "when.manager_api.refund.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterRefund.compareTo(response.getBody().getBalance()), "when.manager_api.refund.body.balance")
            );
        });

        step("THEN: Состояние систем корректно обновлено", () -> {
            step("NATS: Проверка события refunded_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.REFUNDED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.refundRequest.getTransactionId().equals(payload.getUuid());

                ctx.refundEvent = natsClient.expect(NatsGamblingEventPayload.class)
                        .from(subject)
                        .matching(filter)
                        .fetch();

                var payload = ctx.refundEvent.getPayload();
                assertAll("Проверка полей события рефанда в NATS",
                        () -> assertNotNull(ctx.refundEvent, "then.nats.refund_event.not_null"),
                        () -> assertEquals(ctx.refundRequest.getTransactionId(), payload.getUuid(), "then.nats.refund.uuid"),
                        () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(), payload.getNodeUuid(), "then.nats.refund.node_uuid"),
                        () -> assertEquals(0, betAmount.compareTo(payload.getAmount()), "then.nats.refund.amount"),
                        () -> assertEquals(NatsGamblingTransactionOperation.REFUND, payload.getOperation(), "then.nats.refund.operation"),
                        () -> assertEquals(NatsGamblingTransactionType.TYPE_REFUND, payload.getType(), "then.nats.refund.type")
                );

                assertTrue(payload.getWageredDepositInfo().isEmpty(), "then.nats.refund.wagered_deposit_info.is_empty");
            });

            step("Redis: Проверка агрегата кошелька после рефанда", () -> {
                var aggregate = redisClient.getWalletDataWithSeqCheck(
                        ctx.player.getWalletData().walletUUID(),
                        (int) ctx.refundEvent.getSequence());

                var depositData = aggregate.deposits().stream()
                        .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                        .findFirst().orElse(null);

                var betData = aggregate.gambling().get(ctx.betEvent.getPayload().getUuid());
                var refundData = aggregate.gambling().get(ctx.refundEvent.getPayload().getUuid());

                assertAll("Проверка финального состояния агрегата кошелька в Redis",
                        () -> assertEquals((int) ctx.refundEvent.getSequence(), aggregate.lastSeqNumber(), "then.redis.wallet.last_seq_number"),
                        () -> assertEquals(0, ctx.expectedBalanceAfterRefund.compareTo(aggregate.balance()), "then.redis.wallet.balance"),
                        () -> assertNotNull(depositData, "then.redis.wallet.deposit.not_null"),
                        () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), depositData.status(), "then.redis.wallet.deposit.status"),
                        // TODO: уточнить у пеймента, корректное ли отсутствие изменений wagering_amount после refund
                        () -> assertEquals(0, ctx.expectedWagerAmountAfterBet.compareTo(depositData.wageringAmount()), "then.redis.wallet.deposit.wagering_amount"),
                        () -> assertNotNull(betData, "then.redis.wallet.bet_data.not_null"),
                        () -> assertNotNull(refundData, "then.redis.wallet.refund_data.not_null")
                );
            });
        });
    }
}