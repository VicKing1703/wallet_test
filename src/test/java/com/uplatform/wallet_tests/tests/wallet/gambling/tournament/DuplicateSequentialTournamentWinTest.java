package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
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
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Проверяет идемпотентность повторного запроса на турнирный выигрыш с тем же {@code transactionId}.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что при последовательной отправке двух идентичных запросов {@code POST /tournament}
 * второй запрос не меняет состояние и возвращает тот же {@link HttpStatus#OK}, что и первый.
 * Это демонстрирует устойчивость API к повторной доставке сообщений.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Стабильность ответа API:</b>
 *     <p><b>Что проверяем:</b> Что повторная отправка запроса с тем же {@code transactionId}
 *     возвращает успешный статус и неизменный баланс.</p>
 *     <p><b>Почему это важно:</b> Дедупликация запросов гарантирует корректный баланс игрока
 *     при сетевых ретраях и повторной доставке.</p>
 *   </li>
 *   <li><b>Подтверждение первичного события:</b>
 *     <p><b>Что проверяем:</b> Получение NATS-события {@code tournament_won_from_gamble} после первого запроса.</p>
 *     <p><b>Почему это важно:</b> Событие фиксирует факт обработки исходного выигрыша и служит контрольной точкой.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Зарегистрировать игрока и открыть игровую сессию.</li>
 *   <li>Совершить турнирный выигрыш и убедиться в успешном ответе.</li>
 *   <li>Дождаться события {@code tournament_won_from_gamble} в NATS.</li>
 *   <li>Повторно отправить тот же запрос и проверить идемпотентный ответ.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Оба вызова Manager API завершаются со статусом {@link HttpStatus#OK}.</li>
 *   <li>Ответ на второй запрос содержит исходный {@code transactionId} и баланс, равный нулю.</li>
 *   <li>Получено событие NATS для первичной транзакции.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Негативные сценарии: /tournament")
@Tag("Gambling")
@Tag("Wallet7")
class DuplicateSequentialTournamentWinTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal SINGLE_TOURNAMENT_AMOUNT = new BigDecimal("50.00");

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    @Test
    @DisplayName("Дублирование турнирного выигрыша при последовательной отправке идентичных запросов (идемпотентный ответ)")
    void testDuplicateSequentialTournamentWinExpectingValidationError() {

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody firstTournamentRequest;
            NatsMessage<NatsGamblingEventPayload> firstTournamentNatsEvent;
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

        step("Manager API: Совершение первого (успешного) турнирного выигрыша", () -> {
            ctx.firstTournamentRequest = TournamentRequestBody.builder()
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(SINGLE_TOURNAMENT_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .roundId(UUID.randomUUID().toString())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .providerUuid(ctx.gameLaunchData.dbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.firstTournamentRequest),
                    ctx.firstTournamentRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.first_win_status_code");
        });

        step("NATS: Ожидание NATS-события tournament_won_from_gamble для первого турнирного выигрыша", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.firstTournamentNatsEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.firstTournamentRequest.getTransactionId())
                    .fetch();

            assertNotNull(ctx.firstTournamentNatsEvent, "nats.tournament_won_from_gamble_event_for_first_win");
        });

        step("Manager API: Попытка дублирования турнирного выигрыша (повторная отправка с ID: " + ctx.firstTournamentRequest.getTransactionId() + ") и проверка идемпотентного ответа", () -> {
            var duplicateResponse = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.firstTournamentRequest),
                    ctx.firstTournamentRequest
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("Проверка идемпотентного ответа при дублировании турнирного выигрыша",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.tournament.status_code_on_duplicate"),
                    () -> assertNotNull(responseBody, "manager_api.tournament.body_on_duplicate"),
                    () -> assertEquals(ctx.firstTournamentRequest.getTransactionId(), responseBody.transactionId(), "manager_api.tournament.transaction_id_on_duplicate"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.balance()), "manager_api.tournament.balance_on_duplicate")
            );
        });
    }
}
