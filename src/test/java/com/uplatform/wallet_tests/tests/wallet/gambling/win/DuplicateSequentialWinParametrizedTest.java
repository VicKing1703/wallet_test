package com.uplatform.wallet_tests.tests.wallet.gambling.win;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий идемпотентность последовательных запросов на выигрыш.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что повторный {@code transactionId}, отправленный сразу после успешного выигрыша, обрабатывается
 * идемпотентно и не приводит к двойному начислению средств.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Идемпотентность API:</b>
 *     <p><b>Что проверяем:</b> Второй запрос возвращает {@link HttpStatus#OK} и нулевой баланс.</p>
 *     <p><b>Почему это важно:</b> Клиентские ретраи не должны приводить к повторному начислению выигрыша.</p>
 *   </li>
 *   <li><b>Консистентность событий:</b>
 *     <p><b>Что проверяем:</b> В NATS появляется событие только для первого запроса.</p>
 *     <p><b>Почему это важно:</b> Это исключает дублирование в аналитических и отчетных системах.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Создать игрока и игровую сессию.</li>
 *   <li>Совершить базовую ставку.</li>
 *   <li>Отправить первый выигрыш и дождаться события {@code won_from_gamble}.</li>
 *   <li>Повторно отправить идентичный выигрыш и проверить ответ.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Первый выигрыш завершается успешно и публикует событие.</li>
 *   <li>Дубликат возвращает тот же {@code transactionId} и баланс {@link BigDecimal#ZERO}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Негативные сценарии: /win")
@Tag("Gambling") @Tag("Wallet7")
class DuplicateSequentialWinParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal BET_AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal WIN_AMOUNT = new BigDecimal("1.00");

    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    static Stream<Arguments> winOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.WIN, WIN_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.WIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, WIN_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, WIN_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, BigDecimal.ZERO)
        );
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("winOperationAndAmountProvider")
    @DisplayName("Дублирование выигрыша при последовательной отправке")
    void testDuplicateWinReturnsIdempotentResponse(NatsGamblingTransactionOperation operationParam, BigDecimal winAmountParam)  {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody initialBetRequest;
            WinRequestBody firstWinRequest;
            NatsMessage<NatsGamblingEventPayload> firstWinNatsEvent;
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

        step("Manager API: Совершение базовой ставки", () -> {
            ctx.initialBetRequest = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.initialBetRequest),
                    ctx.initialBetRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.base_bet_status_code");
        });

        step("Manager API: Совершение первого выигрыша", () -> {
            ctx.firstWinRequest = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(ctx.initialBetRequest.getRoundId())
                    .roundClosed(false)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.firstWinRequest),
                    ctx.firstWinRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.first_win_status_code");
        });

        step("NATS: Ожидание NATS-события won_from_gamble для первого выигрыша", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.firstWinNatsEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.WON_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.firstWinRequest.getTransactionId())
                    .fetch();

            assertNotNull(ctx.firstWinNatsEvent, "nats.won_from_gamble");
        });

        step("Manager API: Попытка дублирования выигрыша", () -> {
            var duplicateResponse = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.firstWinRequest),
                    ctx.firstWinRequest
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("Проверка ответа на дубликат выигрыша",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.win.duplicate_win_status_code"),
                    () -> assertNotNull(responseBody, "manager_api.win.duplicate_win_response_body"),
                    () -> assertEquals(ctx.firstWinRequest.getTransactionId(), responseBody.transactionId(), "manager_api.win.duplicate_win_transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.balance()), "manager_api.win.duplicate_win_balance")
            );
        });
    }
}
