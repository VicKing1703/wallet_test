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
 * <p>
 * Эти параметризованные тесты покрывают ключевой бизнес-сценарий, при котором игрок,
 * совершивший депозит, должен отыграть его сумму перед выводом. Проверяется, как различные
 * типы игровых транзакций (ставки, чаевые, фриспины) влияют на сумму отыгрыша.
 *
 * <h3>Сценарий: Отыгрыш депозита через игровую транзакцию</h3>
 * <p>В этом сценарии проверяется, что система корректно обновляет сумму отыгрыша депозита
 * после того, как игрок совершает ставку на реальные деньги, и не изменяет ее при использовании фриспинов.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует зарегистрированный и верифицированный игрок.</li>
 *   <li>Игрок сделал депозит, который требует отыгрыша.</li>
 *   <li>У игрока есть активная игровая сессия.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Игрок совершает игровую транзакцию (ставку, чаевые или фриспин) через manager_api.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>manager_api</b>: Отвечает статусом <code>200 OK</code> на запрос <code>/_core_gas_processing/bet</code>.</li>
 *   <li><b>wallet-manager</b>: Отправляет событие <code>betted_from_gamble</code> в NATS.
 *   <ul><li>Для ставок на реальные деньги (BET, TIPS) событие содержит поле <code>wagered_deposit_info</code> с информацией о том, какая часть какого депозита была отыграна.</li>
 *       <li>Для фриспинов (FREESPIN) поле <code>wagered_deposit_info</code> отсутствует или пустое.</li></ul></li>
 *   <li><b>wallet_wallet_redis</b>: Обновляет агрегат кошелька в Redis.
 *   <ul><li>Общий баланс кошелька уменьшается на сумму ставки.</li>
 *       <li>Внутреннее поле <code>wageringAmount</code> у конкретного депозита увеличивается на сумму ставки (если это не фриспин).</li></ul></li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Отыгрыш депозита")
@Tag("Wallet") @Tag("Payment")
public class DepositWageringBetParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("150.00");

    static Stream<Arguments> betParamsProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(DEPOSIT_AMOUNT),
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET + " на нулевую сумму"),
                Arguments.of(
                        DEPOSIT_AMOUNT,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET + " на всю сумму депозита"),
                Arguments.of(
                        generateBigDecimalAmount(DEPOSIT_AMOUNT),
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS + " на нулевую сумму"),
                Arguments.of(
                        DEPOSIT_AMOUNT,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS + " на всю сумму депозита"),
                Arguments.of(
                        generateBigDecimalAmount(DEPOSIT_AMOUNT),
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN + " на случайную сумму (не влияет на отыгрыш)"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN + " на нулевую сумму (не влияет на отыгрыш)"),

                Arguments.of(
                        DEPOSIT_AMOUNT,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN + " на сумму депозита (не влияет на отыгрыш)")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("betParamsProvider")
    @DisplayName("Влияние транзакций типа /bet на отыгрыш депозита:")
    void shouldDepositAndBetAndCheckRedis(
            BigDecimal betAmount,
            NatsGamblingTransactionOperation operationParam,
            String description) {

        final class TestData {
            RegisteredPlayerData player;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequest;
            NatsMessage<NatsGamblingEventPayload> betEvent;

            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedWageredAmount;
        }
        final TestData ctx = new TestData();

        step("GIVEN: Игрок с активным депозитом и игровой сессией", () -> {
            step("Регистрация нового игрока с KYC", () -> {
                ctx.player = defaultTestSteps.registerNewPlayerWithKyc();

                assertNotNull(ctx.player, "setup.player.creation");
            });

            step("Создание игровой сессии", () -> {
                ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.player);

                assertNotNull(ctx.gameLaunchData, "setup.game_session.creation");
            });

            step("Выполнение депозита через FAPI", () -> {
                var depositRequest = DepositRequestBody.builder()
                        .amount(DEPOSIT_AMOUNT.toPlainString())
                        .paymentMethodId(PaymentMethodId.FAKE)
                        .currency(ctx.player.getWalletData().currency())
                        .country(configProvider.getEnvironmentConfig().getPlatform().getCountry())
                        .redirect(DepositRequestBody.RedirectUrls.builder()
                                .failed(DepositRedirect.FAILED.url())
                                .success(DepositRedirect.SUCCESS.url())
                                .pending(DepositRedirect.PENDING.url())
                                .build())
                        .build();

                publicClient.deposit(ctx.player.getAuthorizationResponse().getBody().getToken(), depositRequest);
            });

            step("Проверка получения подтверждающего события о депозите в NATS", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                        .from(subject)
                        .matching((payload, type) -> NatsEventType.DEPOSITED_MONEY.getHeaderValue().equals(type))
                        .fetch();

                assertNotNull(ctx.depositEvent, "precondition.nats.deposit_event.not_found");
            });

            step("Подготовка ожидаемых результатов для последующих проверок", () -> {
                boolean isFreespin = operationParam == NatsGamblingTransactionOperation.FREESPIN;

                ctx.expectedBalanceAfterBet = DEPOSIT_AMOUNT.subtract(betAmount);
                ctx.expectedWageredAmount = isFreespin ? BigDecimal.ZERO : betAmount;
            });
        });

        step("WHEN: Игрок совершает ставку через manager_api", () -> {
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

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("THEN: wallet-manager отправляет событие `betted_from_gamble` в NATS", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().playerUUID(),
                    ctx.player.getWalletData().walletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, type) ->
                    NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(type) &&
                            ctx.betRequest.getTransactionId().equals(payload.getUuid());

            ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .matching(filter)
                    .fetch();

            var wagerInfoList = ctx.betEvent.getPayload().getWageredDepositInfo();

            if (operationParam == NatsGamblingTransactionOperation.FREESPIN) {
                assertTrue(wagerInfoList.isEmpty(), "nats.wager_info.not_empty_for_freespin");
            } else {
                assertFalse(wagerInfoList.isEmpty(), "nats.wager_info.empty_for_bet");
                Map<String, Object> wagerInfo = wagerInfoList.get(0);

                assertAll("Проверка wagered_deposit_info в NATS",
                        () -> assertEquals(ctx.depositEvent.getPayload().getUuid(), wagerInfo.get("deposit_uuid"), "nats.wager_info.deposit_uuid"),
                        () -> assertEquals(0, ctx.expectedWageredAmount.compareTo(new BigDecimal(wagerInfo.get("updated_wagered_amount").toString())), "nats.wager_info.updated_wagered_amount")
                );
            }
        });

        step("THEN: wallet_wallet_redis обновляет баланс и сумму отыгрыша в агрегате Redis", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.player.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.betEvent.getSequence())
                    .fetch();

            var depositData = aggregate.deposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                    .findFirst().orElse(null);

            assertAll("Проверка агрегата кошелька в Redis после ставки",
                    () -> assertEquals((int) ctx.betEvent.getSequence(), aggregate.lastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertNotNull(depositData, "redis.aggregate.deposit_not_found"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterBet.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, ctx.expectedWageredAmount.compareTo(depositData.wageringAmount()), "redis.aggregate.deposit.wagering_amount")
            );
        });
    }
}
