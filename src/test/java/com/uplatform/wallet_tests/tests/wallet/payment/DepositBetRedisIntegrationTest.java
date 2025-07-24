package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
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
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет отыгрыш депозита при совершении ставки и обновление агрегата кошелька в Redis.
 *
 * Тест выполняет регистрацию, запуск игровой сессии, депозит фиксированной суммой
 * и ставку с различными суммами и типами. В событии betted_from_gamble
 * проверяется блок {@code wagered_deposit_info} (при нулевой ставке он
 * отсутствует), а в Redis подтверждается изменение
 * {@code WageringAmount} и статус депозита, а также корректный баланс кошелька.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> полная регистрация с KYC.</li>
 *   <li><b>Создание сессии:</b> старт игровой сессии сразу после регистрации.</li>
 *   <li><b>Депозит:</b> вызов FAPI эндпоинта deposit.</li>
 *   <li><b>Ставка:</b> совершение ставки через Manager API.</li>
 *   <li><b>Проверка NATS:</b> получение deposited_money и betted_from_gamble с блоком wagered_deposit_info.</li>
 *   <li><b>Проверка Redis:</b> депозит содержит обновленный WageringAmount и статус SUCCESS, баланс кошелька корректный.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: /deposit и /bet</li>
 *   <li>NATS</li>
 *   <li>Redis кошелька</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit&Bet")
@Suite("Позитивные сценарии: Deposit -> Bet -> Redis")
@Tag("Wallet") @Tag("Payment")
public class DepositBetRedisIntegrationTest extends BaseParameterizedTest {

    private static final BigDecimal depositAmount = new BigDecimal("150.00");

    static Stream<Arguments> betParamsProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                )
        );
    }

    @ParameterizedTest(name = "тип = {2} и сумма = {0}")
    @MethodSource("betParamsProvider")
    @DisplayName("Регистрация, депозит и ставка с проверкой Redis")
    void shouldDepositAndBetAndCheckRedis(
            BigDecimal betAmount,
            NatsGamblingTransactionOperation operationParam,
            NatsGamblingTransactionType transactionTypeParam) throws Exception {
        final String nodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData player;
            DepositRequestBody depositRequest;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequest;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            BigDecimal expectedBalance;
        }
        final TestData ctx = new TestData();
        ctx.expectedBalance = depositAmount.subtract(betAmount);

        step("Default Step: Полная регистрация игрока с KYC", () -> {
            ctx.player = defaultTestSteps.registerNewPlayerWithKyc();
            assertNotNull(ctx.player, "default_step.registration_with_kyc");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.player);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("FAPI: Выполнение депозита", () -> {
            ctx.depositRequest = DepositRequestBody.builder()
                    .amount(depositAmount.toPlainString())
                    .paymentMethodId(PaymentMethodId.FAKE)
                    .currency(ctx.player.getWalletData().getCurrency())
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

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.deposit.status_code");
        });

        step("NATS: Проверка события deposited_money", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().getPlayerUUID(),
                    ctx.player.getWalletData().getWalletUUID());

            BiPredicate<NatsDepositedMoneyPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.DEPOSITED_MONEY.getHeaderValue().equals(typeHeader);

            ctx.depositEvent = natsClient.findMessageAsync(
                    subject,
                    NatsDepositedMoneyPayload.class,
                    filter).get();

            var payload = ctx.depositEvent.getPayload();
            assertAll("Проверка полей депозита",
                    () -> assertEquals(ctx.depositRequest.getCurrency(), payload.getCurrencyCode(), "nats.deposit.currency_code"),
                    () -> assertEquals(0, depositAmount.compareTo(payload.getAmount()), "nats.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS, payload.getStatus(), "nats.deposit.status"),
                    () -> assertEquals(nodeId, payload.getNodeUuid(), "nats.deposit.node_uuid")
            );
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

            assertAll("Проверка ответа ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertEquals(ctx.betRequest.getTransactionId(), response.getBody().getTransactionId(), "manager_api.body.transactionId")
            );
        });

        step("NATS: Проверка события betted_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().getPlayerUUID(),
                    ctx.player.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.betRequest.getTransactionId().equals(payload.getUuid());

            ctx.betEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            var payload = ctx.betEvent.getPayload();
            assertAll("Проверка полей события ставки",
                    () -> assertEquals(ctx.betRequest.getTransactionId(), payload.getUuid(), "nats.bet.uuid"),
                    () -> assertEquals(nodeId, payload.getNodeUuid(), "nats.bet.node_uuid"),
                    () -> assertEquals(0, betAmount.negate().compareTo(payload.getAmount()), "nats.bet.amount"),
                    () -> assertEquals(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid(), payload.getGameSessionUuid(), "nats.bet.game_session_uuid"),
                    () -> assertEquals(NatsGamblingTransactionOperation.BET, payload.getOperation(), "nats.bet.operation"),
                    () -> assertEquals(transactionTypeParam, payload.getType(), "nats.bet.type")
            );

            var wagerInfoList = payload.getWageredDepositInfo();
            if (betAmount.compareTo(BigDecimal.ZERO) == 0) {
                assertTrue(wagerInfoList.isEmpty(), "nats.bet.wagered_deposit_info.empty");
            } else {
                assertFalse(wagerInfoList.isEmpty(), "nats.bet.wagered_deposit_info.not_empty");
                Map<String, Object> wagerInfo = wagerInfoList.get(0);
                assertAll("Проверка wagered_deposit_info",
                        () -> assertEquals(ctx.depositEvent.getPayload().getUuid(), wagerInfo.get("deposit_uuid"), "nats.bet.wagered_deposit_info.deposit_uuid"),
                        () -> assertEquals(0, betAmount.compareTo(new BigDecimal((String) wagerInfo.get("updated_wagered_amount"))), "nats.bet.wagered_deposit_info.updated_wagered_amount")
                );
            }
        });

        step("Redis(Wallet): Проверка агрегата кошелька после ставки", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.player.getWalletData().getWalletUUID(),
                    (int) ctx.betEvent.getSequence());

            var depositData = aggregate.getDeposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                    .findFirst().orElse(null);

            var gamblingData = aggregate.getGambling().get(ctx.betEvent.getPayload().getUuid());

            assertAll("Проверка агрегата",
                    () -> assertEquals((int) ctx.betEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertNotNull(depositData, "redis.wallet.deposit_not_found"),
                    () -> assertEquals(0, depositAmount.compareTo(depositData.getAmount()), "redis.wallet.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), depositData.getStatus(), "redis.wallet.deposit.status"),
                    () -> assertEquals(0, betAmount.compareTo(depositData.getWageringAmount()), "redis.wallet.deposit.wagering_amount"),
                    () -> assertNotNull(gamblingData, "redis.wallet.gambling_not_found"),
                    () -> assertEquals(0, betAmount.negate().compareTo(gamblingData.getAmount()), "redis.wallet.gambling.amount")
            );
        });
    }
}
