package com.uplatform.wallet_tests.tests.wallet.payment;
import com.testing.multisource.config.modules.http.HttpServiceHelper;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
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
 * <p>
 * Тест гарантирует, что начисление турнирного выигрыша корректно увеличивает баланс игрока,
 * но <strong>не влияет</strong> на прогресс отыгрыша активного депозита.
 *
 * <h3>Сценарий: Неизменность отыгрыша при получении турнирного выигрыша</h3>
 * <p>Проверяется, что баланс обновлен, а отыгрыш - нет.</p>
 *
 * <b>GIVEN:</b>
 * <ul>
 *   <li>Существует новый зарегистрированный и верифицированный игрок.</li>
 *   <li>У игрока есть активная игровая сессия.</li>
 *   <li>Игрок сделал депозит, требующий отыгрыша.</li>
 * </ul>
 *
 * <b>WHEN:</b>
 * <ul><li>Игроку начисляется турнирный выигрыш через manager_api.</li></ul>
 *
 * <b>THEN:</b>
 * <ul>
 *   <li><b>manager_api</b>: Отвечает статусом <code>200 OK</code> на запрос <code>/_core_gas_processing/tournament</code>.</li>
 *   <li><b>wallet-manager</b>: Отправляет событие <code>tournament_won_from_gamble</code> в NATS, которое <strong>не содержит</strong> блок <code>wagered_deposit_info</code>.</li>
 *   <li><b>wallet_wallet_redis</b>: Обновляет агрегат кошелька в Redis:
 *      <ul>
 *          <li>Общий баланс увеличен на сумму турнирного выигрыша.</li>
 *          <li>Сумма отыгрыша (<code>wageringAmount</code>) депозита равна нулю и <strong>не изменилась</strong>.</li>
 *      </ul>
 *   </li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Payment")
@Feature("Deposit")
@Suite("Позитивные сценарии: Отыгрыш депозита")
@Tag("Wallet") @Tag("Payment")
public class DepositWageringTournamentParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal depositAmount = new BigDecimal("150.00");

    static Stream<Arguments> tournamentParamsProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(depositAmount),
                        NatsGamblingTransactionType.TYPE_TOURNAMENT + " на случайную сумму"),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionType.TYPE_TOURNAMENT + " на нулевую сумму")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("tournamentParamsProvider")
    @DisplayName("Влияние транзакций типа /tournament на отыгрыш депозита:")
    void shouldDepositAndTournamentWinAndCheckRedis(
            BigDecimal tournamentAmountParam,
            String testCaseName) {

        final class TestData {
            RegisteredPlayerData player;
            DepositRequestBody depositRequest;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequest;
            NatsMessage<NatsGamblingEventPayload> tournamentEvent;

            BigDecimal expectedBalanceAfterTournament;
        }
        final TestData ctx = new TestData();
        ctx.expectedBalanceAfterTournament = depositAmount.add(tournamentAmountParam);

        step("GIVEN: Существует игрок с активным депозитом и игровой сессией", () -> {
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

                var payload = ctx.depositEvent.getPayload();
                assertAll("Проверка полей NATS-события о депозите",
                        () -> assertNotNull(ctx.depositEvent, "given.nats.deposit_event.not_null"),
                        () -> assertEquals(ctx.depositRequest.getCurrency(), payload.getCurrencyCode(), "given.nats.deposit.currency_code"),
                        () -> assertEquals(0, depositAmount.compareTo(payload.getAmount()), "given.nats.deposit.amount"),
                        () -> assertEquals(NatsDepositStatus.SUCCESS, payload.getStatus(), "given.nats.deposit.status"),
                        () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(), payload.getNodeUuid(), "given.nats.deposit.node_uuid")
                );
            });
        });

        step("WHEN: Игроку начисляется турнирный выигрыш", () -> {
            ctx.tournamentRequest = TournamentRequestBody.builder()
                    .amount(tournamentAmountParam)
                    .playerId(ctx.player.getWalletData().walletUUID())
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(ctx.gameLaunchData.getDbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp()),
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.tournamentRequest),
                    ctx.tournamentRequest);

            assertAll("Проверка ответа на начисление турнирного выигрыша",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "when.manager_api.tournament.status_code"),
                    () -> assertEquals(ctx.tournamentRequest.getTransactionId(), response.getBody().getTransactionId(), "when.manager_api.tournament.transaction_id"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(response.getBody().getBalance()), "when.manager_api.tournament.balance")
            );
        });

        step("THEN: Состояние систем корректно обновлено", () -> {
            step("NATS: Проверка события tournament_won_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.player.getWalletData().playerUUID(),
                        ctx.player.getWalletData().walletUUID());

                ctx.tournamentEvent = natsClient.expect(NatsGamblingEventPayload.class)
                        .from(subject)
                        .withType(NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue())
                        .with("$.uuid", ctx.tournamentRequest.getTransactionId())
                        .fetch();

                var payload = ctx.tournamentEvent.getPayload();
                assertAll("Проверка полей события турнира в NATS",
                        () -> assertNotNull(ctx.tournamentEvent, "then.nats.tournament_event.not_null"),
                        () -> assertEquals(ctx.tournamentRequest.getTransactionId(), payload.getUuid(), "then.nats.tournament.uuid"),
                        () -> assertEquals(configProvider.getEnvironmentConfig().getPlatform().getNodeId(), payload.getNodeUuid(), "then.nats.tournament.node_uuid"),
                        () -> assertEquals(0, tournamentAmountParam.compareTo(payload.getAmount()), "then.nats.tournament.amount"),
                        () -> assertEquals(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid(), payload.getGameSessionUuid(), "then.nats.tournament.game_session_uuid"),
                        () -> assertEquals(NatsGamblingTransactionOperation.TOURNAMENT, payload.getOperation(), "then.nats.tournament.operation"),
                        () -> assertEquals(NatsGamblingTransactionType.TYPE_TOURNAMENT, payload.getType(), "then.nats.tournament.type")
                );

                assertTrue(payload.getWageredDepositInfo().isEmpty(), "then.nats.tournament.wagered_deposit_info.is_empty");
            });

            step("Redis: Проверка агрегата кошелька после выигрыша", () -> {
                var aggregate = redisWalletClient
                        .key(ctx.player.getWalletData().walletUUID())
                        .withAtLeast("LastSeqNumber", (int) ctx.tournamentEvent.getSequence())
                        .fetch();

                var depositData = aggregate.deposits().stream()
                        .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                        .findFirst().orElse(null);

                var tournamentData = aggregate.gambling().get(ctx.tournamentEvent.getPayload().getUuid());

                assertAll("Проверка финального состояния агрегата кошелька в Redis",
                        () -> assertEquals((int) ctx.tournamentEvent.getSequence(), aggregate.lastSeqNumber(), "then.redis.wallet.last_seq_number"),
                        () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(aggregate.balance()), "then.redis.wallet.balance"),
                        () -> assertNotNull(depositData, "then.redis.wallet.deposit.not_null"),
                        () -> assertEquals(0, depositAmount.compareTo(depositData.amount()), "then.redis.wallet.deposit.amount"),
                        () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), depositData.status(), "then.redis.wallet.deposit.status"),
                        () -> assertEquals(0, BigDecimal.ZERO.compareTo(depositData.wageringAmount()), "then.redis.wallet.deposit.wagering_amount_is_zero"),
                        () -> assertNotNull(tournamentData, "then.redis.wallet.tournament_data.not_null"),
                        () -> assertEquals(0, tournamentAmountParam.compareTo(tournamentData.getAmount()), "then.redis.wallet.tournament_data.amount")
                );
            });
        });
    }
}
