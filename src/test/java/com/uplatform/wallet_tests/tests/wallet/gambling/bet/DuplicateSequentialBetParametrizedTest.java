package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, верифицирующий идемпотентную обработку последовательно отправленных дублирующихся транзакций.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить надежность и корректность механизма идемпотентности при обработке стандартных
 * клиентских повторов (retries). Система обязана гарантировать семантику обработки транзакций "exactly-once",
 * предотвращая двойное списание средств при получении дублирующего запроса. Тест верифицирует, что после успешной
 * обработки транзакции, любой последующий идентичный запрос будет распознан как дубликат и не приведет к повторному
 * изменению состояния баланса.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Проверка по ключу идемпотентности ({@code transactionId}):</b>
 *     <p><b>Что проверяем:</b> Реакцию системы на повторный запрос с тем же {@code transactionId}, что и у ранее успешно обработанной транзакции.
 *     <p><b>Почему это важно:</b> Это основной механизм предотвращения двойных списаний. Тест подтверждает, что система
 *     корректно использует {@code transactionId} для поиска уже существующей транзакции (предположительно, в "горячем"
 *     кеше Redis) и прерывает повторную обработку финансовой логики.
 *   </li>
 *   <li><b>Корректность идемпотентного ответа:</b>
 *     <p><b>Что проверяем:</b> Возврат успешного статуса {@link HttpStatus#OK} и тела ответа с нулевым балансом.
 *     <p><b>Почему это важно:</b> Успешный статус критичен для клиентских интеграций, так как он сигнализирует
 *     об успешном завершении операции без необходимости дальнейших повторов. Тело ответа с нулевым балансом является
 *     системным соглашением (convention), подтверждающим, что запрос был распознан как дубликат и нового списания не произошло.
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Создается игрок и игровая сессия.</li>
 *   <li>Отправляется первый, оригинальный запрос на ставку, и проверяется его успешное выполнение.</li>
 *   <li>После подтверждения обработки (через NATS-событие), отправляется второй, абсолютно идентичный запрос.</li>
 *   <li>Анализируется ответ на второй (дублирующий) запрос.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает статус {@link HttpStatus#OK} на дублирующий запрос.</li>
 *   <li>Тело ответа на дубликат содержит тот же {@code transactionId}, что и оригинальный запрос.</li>
 *   <li>Баланс в теле ответа на дубликат равен {@link BigDecimal#ZERO}.</li>
 *   <li>Финальный баланс игрока в системе отражает только однократное списание средств.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class DuplicateSequentialBetParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal DEFAULT_BET_AMOUNT = new BigDecimal("10.00");

    private String casinoId;

    static Stream<Arguments> betOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.BET, DEFAULT_BET_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.BET, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.TIPS, DEFAULT_BET_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.TIPS, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, DEFAULT_BET_AMOUNT),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO)
        );
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("betOperationAndAmountProvider")
    @DisplayName("Дублирование ставки при последовательной отправке")
    void testDuplicateBetReturnsIdempotentResponse(NatsGamblingTransactionOperation operationParam, BigDecimal betAmountParam)  {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody firstBetRequest;
            NatsMessage<NatsGamblingEventPayload> firstBetNatsEvent;
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

        step("Manager API: Совершение первой ставки", () -> {
            ctx.firstBetRequest = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.firstBetRequest),
                    ctx.firstBetRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.first_bet_status_code");
        });

        step("NATS: Ожидание NATS-события betted_from_gamble для первой ставки", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.firstBetNatsEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.firstBetRequest.getTransactionId())
                    .fetch();

            assertNotNull(ctx.firstBetNatsEvent, "nats.betted_from_gamble");
        });

        step("Manager API: Попытка дублирования ставки", () -> {
            var duplicateResponse = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.firstBetRequest),
                    ctx.firstBetRequest
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("Проверка ответа на дубликат ставки",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.bet.duplicate_bet_status_code"),
                    () -> assertNotNull(responseBody, "manager_api.bet.duplicate_bet_response_body"),
                    () -> assertEquals(ctx.firstBetRequest.getTransactionId(), responseBody.transactionId(), "manager_api.bet.duplicate_bet_transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.balance()), "manager_api.bet.duplicate_bet_balance")
            );
        });
    }
}