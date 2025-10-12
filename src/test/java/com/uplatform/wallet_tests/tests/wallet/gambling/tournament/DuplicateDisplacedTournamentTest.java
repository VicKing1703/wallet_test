package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Проверяет идемпотентную обработку повторного турнирного начисления, вытесненного из кеша Redis.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что {@code POST /tournament} возвращает стабильный {@link HttpStatus#OK},
 * даже если исходная транзакция отсутствует в Redis, но сохранена в долговременном хранилище.
 * Это подтверждает консистентность данных после вытеснения и защиту от повторной обработки выигрышей.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Определение вытесненной транзакции:</b>
 *     <p><b>Что проверяем:</b> Правильную идентификацию {@code transactionId}, отсутствующего в кешевой истории после
 *     выполнения {@value #TOURNAMENTS_TO_DISPLACE} выигрышей.</p>
 *     <p><b>Почему это важно:</b> Только корректно найденный идентификатор гарантирует, что идемпотентность проверяется
 *     на актуальном бизнес-кейсе вытеснения.</p>
 *   </li>
 *   <li><b>Повторный запрос в Manager API:</b>
 *     <p><b>Что проверяем:</b> Ответ сервиса на повторное начисление с тем же {@code transactionId}.</p>
 *     <p><b>Почему это важно:</b> Система не должна изменять баланс и обязана вернуть идентичный успешный ответ,
 *     даже если исходная запись отсутствует в Redis.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Зарегистрировать игрока и открыть игровую сессию.</li>
 *   <li>Совершить {@value #TOURNAMENTS_TO_DISPLACE} последовательных турнирных выигрышей.</li>
 *   <li>Определить транзакцию, вытесненную из Redis.</li>
 *   <li>Отправить повторный запрос и убедиться в идемпотентном ответе.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Manager API возвращает {@link HttpStatus#OK} с исходным {@code transactionId}.</li>
 *   <li>Баланс в ответе равен нулю, подтверждая отсутствие повторного начисления.</li>
 *   <li>Redis содержит только последние записи, а вытесненная транзакция отсутствует в кеше.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Негативные сценарии: /tournament")
@Tag("Gambling")
@Tag("Wallet")
class DuplicateDisplacedTournamentTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal SINGLE_TOURNAMENT_AMOUNT = new BigDecimal("10.00");
    private static final int MAX_GAMBLING_COUNT_IN_REDIS = 50;
    private static final int TOURNAMENTS_TO_DISPLACE = MAX_GAMBLING_COUNT_IN_REDIS + 1;

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    @Test
    @DisplayName("Дублирование турнирного начисления, вытесненного из кеша (ожидается ошибка валидации)")
    void testDuplicateDisplacedTournamentExpectingValidationError() {

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<TournamentRequestBody> allMadeTournamentRequests = new ArrayList<>();
            String lastMadeTournamentTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastTournamentNatsEvent;
            TournamentRequestBody displacedTournamentRequestToDuplicate;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Совершение турнирных начислений для вытеснения", () -> {
            for (int i = 0; i < TOURNAMENTS_TO_DISPLACE; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == TOURNAMENTS_TO_DISPLACE - 1) {
                    ctx.lastMadeTournamentTransactionId = transactionId;
                }
                var tournamentRequestBody = TournamentRequestBody.builder()
                        .playerId(ctx.registeredPlayer.walletData().walletUUID())
                        .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                        .amount(SINGLE_TOURNAMENT_AMOUNT)
                        .transactionId(transactionId)
                        .roundId(UUID.randomUUID().toString())
                        .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                        .providerUuid(ctx.gameLaunchData.dbGameSession().getProviderUuid())
                        .build();
                ctx.allMadeTournamentRequests.add(tournamentRequestBody);

                var currentTournamentNumber = i + 1;
                step("Совершение турнирного начисления #" + currentTournamentNumber + " (ID: " + transactionId + ")", () -> {
                    var response = managerClient.tournament(
                            casinoId,
                            utils.createSignature(ApiEndpoints.TOURNAMENT, tournamentRequestBody),
                            tournamentRequestBody);

                    assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code");
                });
            }
        });

        step("NATS: Ожидание NATS-события tournament_won_from_gamble для последнего начисления", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.lastTournamentNatsEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.lastMadeTournamentTransactionId)
                    .fetch();

            assertNotNull(ctx.lastTournamentNatsEvent, "nats.tournament_won_from_gamble_event");
        });

        step("Redis: Определение вытесненного турнирного начисления", () -> {
            WalletFullData aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.lastTournamentNatsEvent.getSequence())
                    .fetch();

            var gamblingTransactionsInRedis = aggregate.gambling();
            var transactionIdsCurrentlyInRedis = gamblingTransactionsInRedis.keySet();

            var displacedTransactionIds = ctx.allMadeTournamentRequests.stream()
                    .map(TournamentRequestBody::getTransactionId).collect(Collectors.toCollection(HashSet::new));
            displacedTransactionIds.removeAll(transactionIdsCurrentlyInRedis);
            assertEquals(1, displacedTransactionIds.size(), "redis.displaced_transaction.count");
            var displacedTxId = displacedTransactionIds.iterator().next();

            ctx.displacedTournamentRequestToDuplicate = ctx.allMadeTournamentRequests.stream()
                    .filter(req -> req.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));
        });

        step("Manager API: Попытка дублирования вытесненного турнирного начисления (ID: " + ctx.displacedTournamentRequestToDuplicate.getTransactionId() + ") и проверка ошибки", () -> {
            var duplicateTournamentAttemptRequest = TournamentRequestBody.builder()
                    .playerId(ctx.registeredPlayer.walletData().playerUUID())
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(ctx.displacedTournamentRequestToDuplicate.getAmount())
                    .transactionId(ctx.displacedTournamentRequestToDuplicate.getTransactionId())
                    .roundId(ctx.displacedTournamentRequestToDuplicate.getRoundId())
                    .gameUuid(ctx.displacedTournamentRequestToDuplicate.getGameUuid())
                    .providerUuid(ctx.displacedTournamentRequestToDuplicate.getProviderUuid())
                    .build();

            var duplicateResponse = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, duplicateTournamentAttemptRequest),
                    duplicateTournamentAttemptRequest
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("Проверка идемпотентного ответа на дубликат вытесненного турнирного начисления",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.status_code"),
                    () -> assertNotNull(responseBody, "manager_api.response_body"),
                    () -> assertEquals(duplicateTournamentAttemptRequest.getTransactionId(), responseBody.transactionId(), "manager_api.transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.balance()), "manager_api.balance")
            );
        });
    }
}
