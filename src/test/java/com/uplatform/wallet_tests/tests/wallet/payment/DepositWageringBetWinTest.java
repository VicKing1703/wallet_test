package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет неизменность wagered_deposit_info после получения выигрыша.
 *
 * Тест выполняет регистрацию, депозит, ставку и выигрыш различных типов.
 * В событии {@code won_from_gamble} блок {@code wagered_deposit_info}
 * отсутствует, а значение {@code WageringAmount} депозита не меняется
 * после выплаты выигрыша. Баланс кошелька соответствует расчету.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> полная регистрация с KYC.</li>
 *   <li><b>Создание сессии:</b> старт игровой сессии сразу после регистрации.</li>
 *   <li><b>Депозит:</b> вызов FAPI эндпоинта deposit.</li>
 *   <li><b>Ставка:</b> совершение ставки через Manager API.</li>
 *   <li><b>Выигрыш:</b> получение выигрыша через Manager API.</li>
 *   <li><b>Проверка NATS:</b> deposited_money, betted_from_gamble и won_from_gamble.</li>
 *   <li><b>Проверка Redis:</b> депозит содержит неизмененный WageringAmount и корректный баланс кошелька.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: /deposit, /bet и /win</li>
 *   <li>NATS</li>
 *   <li>Redis кошелька</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Отыгрыш депозита")
@Tag("Wallet") @Tag("Payment")
public class DepositWageringBetWinTest extends BaseParameterizedTest {

    private static final BigDecimal depositAmount = new BigDecimal("150.00");

    static Stream<Arguments> winParamsProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.WIN,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.WIN,
                        NatsGamblingTransactionType.TYPE_WIN
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
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.JACKPOT,
                        NatsGamblingTransactionType.TYPE_JACKPOT
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.JACKPOT,
                        NatsGamblingTransactionType.TYPE_JACKPOT
                )
        );
    }

    @ParameterizedTest(name = "тип транзакции = {2} и сумма = {0}")
    @MethodSource("winParamsProvider")
    @DisplayName("Отыгрыш депозита:")
    void shouldDepositBetWinAndCheckRedis(
            BigDecimal winAmountParam,
            NatsGamblingTransactionOperation operationParam,
            NatsGamblingTransactionType transactionTypeParam) throws Exception {
        final String nodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData player;
            DepositRequestBody depositRequest;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequest;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            WinRequestBody winRequest;
            NatsMessage<NatsGamblingEventPayload> winEvent;
            BigDecimal betAmount;
            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterWin;
            BigDecimal expectedWagerAmount;
        }
        final TestData ctx = new TestData();
        ctx.betAmount = generateBigDecimalAmount(depositAmount);
        ctx.expectedBalanceAfterBet = depositAmount.subtract(ctx.betAmount);
        ctx.expectedBalanceAfterWin = ctx.expectedBalanceAfterBet.add(winAmountParam);
        ctx.expectedWagerAmount = ctx.betAmount;

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

            ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

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
                    .amount(ctx.betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequest),
                    ctx.betRequest);

            assertAll("Проверка ответа ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code"),
                    () -> assertEquals(ctx.betRequest.getTransactionId(), response.getBody().getTransactionId(), "manager_api.bet.body.transactionId")
            );
        });

        step("NATS: Проверка события betted_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().getPlayerUUID(),
                    ctx.player.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.betRequest.getTransactionId().equals(payload.getUuid());

            ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var payload = ctx.betEvent.getPayload();
            assertAll("Проверка полей события ставки",
                    () -> assertEquals(ctx.betRequest.getTransactionId(), payload.getUuid(), "nats.bet.uuid"),
                    () -> assertEquals(nodeId, payload.getNodeUuid(), "nats.bet.node_uuid"),
                    () -> assertEquals(0, ctx.betAmount.negate().compareTo(payload.getAmount()), "nats.bet.amount"),
                    () -> assertEquals(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid(), payload.getGameSessionUuid(), "nats.bet.game_session_uuid"),
                    () -> assertEquals(NatsGamblingTransactionOperation.BET, payload.getOperation(), "nats.bet.operation"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_BET, payload.getType(), "nats.bet.type")
            );

            var wagerInfoList = payload.getWageredDepositInfo();
            assertFalse(wagerInfoList.isEmpty(), "nats.bet.wagered_deposit_info.not_empty");
            Map<String, Object> wagerInfo = wagerInfoList.get(0);
            assertAll("Проверка wagered_deposit_info",
                    () -> assertEquals(ctx.depositEvent.getPayload().getUuid(), wagerInfo.get("deposit_uuid"), "nats.bet.wagered_deposit_info.deposit_uuid"),
                    () -> assertEquals(0, ctx.expectedWagerAmount.compareTo(new BigDecimal((String) wagerInfo.get("updated_wagered_amount"))), "nats.bet.wagered_deposit_info.updated_wagered_amount")
            );
        });

        step("Manager API: Получение выигрыша", () -> {
            ctx.winRequest = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(ctx.betRequest.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.winRequest),
                    ctx.winRequest);

            assertAll("Проверка ответа выигрыша",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code"),
                    () -> assertEquals(ctx.winRequest.getTransactionId(), response.getBody().getTransactionId(), "manager_api.win.body.transactionId")
            );
        });

        step("NATS: Проверка события won_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().getPlayerUUID(),
                    ctx.player.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.winRequest.getTransactionId().equals(payload.getUuid());

            ctx.winEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var payload = ctx.winEvent.getPayload();
            assertAll("Проверка полей события выигрыша",
                    () -> assertEquals(ctx.winRequest.getTransactionId(), payload.getUuid(), "nats.win.uuid"),
                    () -> assertEquals(nodeId, payload.getNodeUuid(), "nats.win.node_uuid"),
                    () -> assertEquals(0, winAmountParam.compareTo(payload.getAmount()), "nats.win.amount"),
                    () -> assertEquals(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid(), payload.getGameSessionUuid(), "nats.win.game_session_uuid"),
                    () -> assertEquals(NatsGamblingTransactionOperation.WIN, payload.getOperation(), "nats.win.operation"),
                    () -> assertEquals(transactionTypeParam, payload.getType(), "nats.win.type")
            );

            assertTrue(payload.getWageredDepositInfo().isEmpty(), "nats.win.wagered_deposit_info.empty");
        });

        step("Redis(Wallet): Проверка агрегата кошелька после выигрыша", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.player.getWalletData().getWalletUUID(),
                    (int) ctx.winEvent.getSequence());

            var depositData = aggregate.getDeposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                    .findFirst().orElse(null);

            var winData = aggregate.getGambling().get(ctx.winEvent.getPayload().getUuid());
            var betData = aggregate.getGambling().get(ctx.betEvent.getPayload().getUuid());

            assertAll("Проверка агрегата",
                    () -> assertEquals((int) ctx.winEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertNotNull(depositData, "redis.wallet.deposit_not_found"),
                    () -> assertEquals(0, depositAmount.compareTo(depositData.getAmount()), "redis.wallet.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), depositData.getStatus(), "redis.wallet.deposit.status"),
                    () -> assertEquals(0, ctx.expectedWagerAmount.compareTo(depositData.getWageringAmount()), "redis.wallet.deposit.wagering_amount"),
                    () -> assertNotNull(betData, "redis.wallet.bet_not_found"),
                    () -> assertEquals(0, ctx.betAmount.negate().compareTo(betData.getAmount()), "redis.wallet.bet.amount"),
                    () -> assertNotNull(winData, "redis.wallet.win_not_found"),
                    () -> assertEquals(0, winAmountParam.compareTo(winData.getAmount()), "redis.wallet.win.amount")
            );
        });
    }
}
