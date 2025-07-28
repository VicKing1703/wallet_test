package com.uplatform.wallet_tests.tests.wallet.payment;

import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.DepositRedirect;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.enums.PaymentMethodId;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет неизменность wagered_deposit_info при получении турнирного выигрыша.
 *
 * Тест выполняет регистрацию, депозит и начисление выигрыша турнира.
 * В событии {@code tournament_won_from_gamble} блок {@code wagered_deposit_info}
 * отсутствует, а значение {@code WageringAmount} депозита не меняется после
 * начисления выигрыша. Баланс кошелька соответствует расчету.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> полная регистрация с KYC.</li>
 *   <li><b>Создание сессии:</b> старт игровой сессии сразу после регистрации.</li>
 *   <li><b>Депозит:</b> вызов FAPI эндпоинта deposit.</li>
 *   <li><b>Турнирный выигрыш:</b> начисление выигрыша через Manager API.</li>
 *   <li><b>Проверка NATS:</b> deposited_money и tournament_won_from_gamble.</li>
 *   <li><b>Проверка Redis:</b> депозит содержит неизмененный WageringAmount и корректный баланс кошелька.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: /deposit и /tournament</li>
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
public class DepositWageringTournamentTest extends BaseTest {

    private static final BigDecimal depositAmount = new BigDecimal("150.00");

    @Test
    @DisplayName("Отыгрыш депозита: тип транзакции = TYPE_TOURNAMENT")
    void shouldDepositAndTournamentWinAndCheckRedis() throws Exception {
        final String nodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData player;
            DepositRequestBody depositRequest;
            NatsMessage<NatsDepositedMoneyPayload> depositEvent;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequest;
            NatsMessage<NatsGamblingEventPayload> tournamentEvent;
            BigDecimal tournamentAmount;
            BigDecimal expectedBalanceAfterTournament;
        }
        final TestData ctx = new TestData();
        ctx.tournamentAmount = generateBigDecimalAmount(depositAmount);
        ctx.expectedBalanceAfterTournament = depositAmount.add(ctx.tournamentAmount);

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

        step("Manager API: Начисление турнирного выигрыша", () -> {
            ctx.tournamentRequest = TournamentRequestBody.builder()
                    .amount(ctx.tournamentAmount)
                    .playerId(ctx.player.getWalletData().getWalletUUID())
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(ctx.gameLaunchData.getDbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.tournamentRequest),
                    ctx.tournamentRequest);

            assertAll("Проверка ответа турнира",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code"),
                    () -> assertEquals(ctx.tournamentRequest.getTransactionId(), response.getBody().getTransactionId(), "manager_api.tournament.transaction_id"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(response.getBody().getBalance()), "manager_api.tournament.balance")
            );
        });

        step("NATS: Проверка события tournament_won_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.player.getWalletData().getPlayerUUID(),
                    ctx.player.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.tournamentRequest.getTransactionId().equals(payload.getUuid());

            ctx.tournamentEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            var payload = ctx.tournamentEvent.getPayload();
            assertAll("Проверка полей события турнира",
                    () -> assertEquals(ctx.tournamentRequest.getTransactionId(), payload.getUuid(), "nats.tournament.uuid"),
                    () -> assertEquals(nodeId, payload.getNodeUuid(), "nats.tournament.node_uuid"),
                    () -> assertEquals(0, ctx.tournamentAmount.compareTo(payload.getAmount()), "nats.tournament.amount"),
                    () -> assertEquals(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid(), payload.getGameSessionUuid(), "nats.tournament.game_session_uuid"),
                    () -> assertEquals(NatsGamblingTransactionOperation.TOURNAMENT, payload.getOperation(), "nats.tournament.operation"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_TOURNAMENT, payload.getType(), "nats.tournament.type")
            );

            assertTrue(payload.getWageredDepositInfo().isEmpty(), "nats.tournament.wagered_deposit_info.empty");
        });

        step("Redis(Wallet): Проверка агрегата кошелька после выигрыша", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.player.getWalletData().getWalletUUID(),
                    (int) ctx.tournamentEvent.getSequence());

            var depositData = aggregate.getDeposits().stream()
                    .filter(d -> d.getUuid().equals(ctx.depositEvent.getPayload().getUuid()))
                    .findFirst().orElse(null);

            var tournamentData = aggregate.getGambling().get(ctx.tournamentEvent.getPayload().getUuid());

            assertAll("Проверка агрегата",
                    () -> assertEquals((int) ctx.tournamentEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertNotNull(depositData, "redis.wallet.deposit_not_found"),
                    () -> assertEquals(0, depositAmount.compareTo(depositData.getAmount()), "redis.wallet.deposit.amount"),
                    () -> assertEquals(NatsDepositStatus.SUCCESS.getValue(), depositData.getStatus(), "redis.wallet.deposit.status"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(depositData.getWageringAmount()), "redis.wallet.deposit.wagering_amount"),
                    () -> assertNotNull(tournamentData, "redis.wallet.tournament_not_found"),
                    () -> assertEquals(0, ctx.tournamentAmount.compareTo(tournamentData.getAmount()), "redis.wallet.tournament.amount")
            );
        });
    }
}
