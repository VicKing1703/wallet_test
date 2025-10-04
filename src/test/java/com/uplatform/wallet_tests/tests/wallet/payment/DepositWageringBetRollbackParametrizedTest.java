package com.uplatform.wallet_tests.tests.wallet.payment;
import com.testing.multisource.config.modules.http.HttpServiceHelper;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsDepositedMoneyPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
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
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>Тест гарантирует, что после отката (роллбэка) ставки, которая изначально
 * влияла на отыгрыш, сумма отыгрыша депозита корректно откатывается.</p>
 *
 * <h3>Сценарий: Откат отыгрыша после роллбэка ставки</h3>
 * <p>Проверяется полный жизненный цикл: депозит -> ставка -> роллбэк.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует зарегистрированный и верифицированный игрок.</li>
 *   <li>Игрок сделал депозит, требующий отыгрыша.</li>
 *   <li>Игрок сделал ставку, которая увеличила сумму отыгрыша.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Происходит откат (роллбэк) этой ставки через manager_api.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>manager_api</b>: Отвечает статусом <code>200 OK</code> на запросы <code>/_core_gas_processing/bet</code> и <code>/_core_gas_processing/rollback</code>.</li>
 *   <li><b>wallet-manager</b>: Отправляет события в NATS:
 *      <ul>
 *          <li><code>betted_from_gamble</code>: Содержит информацию об увеличении отыгрыша.</li>
 *          <li><code>rollbacked_from_gamble</code>: <strong>Не содержит</strong> блок <code>wagered_deposit_info</code>, но инициирует откат отыгрыша.</li>
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
@Tag("Wallet7") @Tag("Payment")
public class DepositWageringBetRollbackParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal depositAmount = new BigDecimal("150.00");

    static Stream<Arguments> betRollbackParamsProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.BET,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_BET + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_BET + " на нулевую сумму"),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.BET,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_BET + " на всю сумму депозита"),
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.TIPS,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_TIPS + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_TIPS + " на нулевую сумму"),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.TIPS,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_TIPS + " на всю сумму депозита"),
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionOperation.FREESPIN,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_FREESPIN + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_FREESPIN + " на нулевую сумму"),
                Arguments.of(
                        depositAmount,
                        NatsGamblingTransactionOperation.FREESPIN,
                        "Роллбэк для " + NatsGamblingTransactionType.TYPE_FREESPIN + " на всю сумму депозита")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("betRollbackParamsProvider")
    @DisplayName("Влияние транзакций типа /rollback на отыгрыш депозита:")
    void shouldDepositBetRollbackAndCheckRedis(
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
            RollbackRequestBody rollbackRequest;
            NatsMessage<NatsGamblingEventPayload> rollbackEvent;

            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterRollback;
            BigDecimal expectedWagerAmountAfterBet;
        }
        final TestData ctx = new TestData();
        ctx.expectedBalanceAfterBet = operationParam == NatsGamblingTransactionOperation.FREESPIN ? depositAmount : depositAmount.subtract(betAmount);
        ctx.expectedBalanceAfterRollback = depositAmount;
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

                ctx.depositEvent = natsClient.expect(NatsDepositedMoneyPayload.class)
                        .from(subject)
                        .withType(NatsEventType.DEPOSITED_MONEY.getHeaderValue())
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
                        HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp()),
                        utils.createSignature(ApiEndpoints.BET, ctx.betRequest),
                        ctx.betRequest);

                assertEquals(HttpStatus.OK, response.getStatusCode(), "given.manager_api.bet.status_code");
            });

            step("NATS: Проверка события betted_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
                        .from(subject)
                        .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
                        .with("$.uuid", ctx.betRequest.getTransactionId())
                        .fetch();

                assertNotNull(ctx.betEvent, "given.nats.bet_event.not_null");
            });
        });

        step("WHEN: Выполняется роллбэк ставки", () -> {
            ctx.rollbackRequest = RollbackRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(ctx.betRequest.getTransactionId())
                    .currency(ctx.player.getWalletData().currency())
                    .playerId(ctx.player.getWalletData().walletUUID())
                    .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(ctx.betRequest.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.rollback(
                    HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp()),
                    utils.createSignature(ApiEndpoints.ROLLBACK, ctx.rollbackRequest),
                    ctx.rollbackRequest);

            assertAll("Проверка ответа на роллбэк от Manager API",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "when.manager_api.rollback.status_code"),
                    () -> assertEquals(ctx.rollbackRequest.getTransactionId(), response.getBody().transactionId(), "when.manager_api.rollback.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterRollback.compareTo(response.getBody().balance()), "when.manager_api.rollback.body.balance")
            );
        });

        step("THEN: Состояние систем корректно обновлено", () -> {
            step("NATS: Проверка события rollbacked_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                ctx.rollbackEvent = natsClient.expect(NatsGamblingEventPayload.class)
                        .from(subject)
                        .withType(NatsEventType.ROLLBACKED_FROM_GAMBLE.getHeaderValue())
                        .with("$.uuid", ctx.rollbackRequest.getTransactionId())
                        .fetch();

                var payload = ctx.rollbackEvent.getPayload();
                assertAll("Проверка полей события роллбэка в NATS",
                        () -> assertNotNull(ctx.rollbackEvent, "then.nats.rollback_event.not_null"),
                        () -> assertEquals(ctx.rollbackRequest.getTransactionId(), payload.uuid(), "then.nats.rollback.uuid"),
                        () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(), payload.nodeUuid(), "then.nats.rollback.node_uuid"),
                        () -> assertEquals(0, betAmount.compareTo(payload.amount()), "then.nats.rollback.amount"),
                        () -> assertEquals(NatsGamblingTransactionOperation.ROLLBACK, payload.operation(), "then.nats.rollback.operation"),
                        () -> assertEquals(NatsGamblingTransactionType.TYPE_ROLLBACK, payload.type(), "then.nats.rollback.type")
                );

                assertTrue(payload.wageredDepositInfo().isEmpty(), "then.nats.rollback.wagered_deposit_info.is_empty");
            });

            step("Redis: Проверка агрегата кошелька после роллбэка", () -> {
                var aggregate = redisWalletClient
                        .key(ctx.player.getWalletData().walletUUID())
                        .withAtLeast("LastSeqNumber", (int) ctx.rollbackEvent.getSequence())
                        .fetch();

                var depositData = aggregate.deposits().stream()
                        .filter(d -> d.uuid().equals(ctx.depositEvent.getPayload().uuid()))
                        .findFirst().orElse(null);

                var betData = aggregate.gambling().get(ctx.betEvent.getPayload().uuid());
                var rollbackData = aggregate.gambling().get(ctx.rollbackEvent.getPayload().uuid());

                assertAll("Проверка финального состояния агрегата кошелька в Redis",
                        () -> assertEquals((int) ctx.rollbackEvent.getSequence(), aggregate.lastSeqNumber(), "then.redis.wallet.last_seq_number"),
                        () -> assertEquals(0, ctx.expectedBalanceAfterRollback.compareTo(aggregate.balance()), "then.redis.wallet.balance"),
                        () -> assertNotNull(depositData, "then.redis.wallet.deposit.not_null"),
                        () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), depositData.status(), "then.redis.wallet.deposit.status"),
                        // TODO: уточнить у пеймента, корректное ли отсутствие изменений wagering_amount после rollback
                        () -> assertEquals(0, ctx.expectedWagerAmountAfterBet.compareTo(depositData.wageringAmount()), "then.redis.wallet.deposit.wagering_amount"),
                        () -> assertNotNull(betData, "then.redis.wallet.bet_data.not_null"),
                        () -> assertNotNull(rollbackData, "then.redis.wallet.rollback_data.not_null")
                );
            });
        });
    }
}
