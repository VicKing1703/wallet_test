package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, верифицирующий отказоустойчивость механизма идемпотентности при обработке дубликата транзакции, вытесненной из кеша.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить надежность двухуровневой системы проверки идемпотентности. Тест эмулирует сценарий,
 * когда дублирующий запрос поступает для транзакции, которая из-за высокой нагрузки или по истечении времени была вытеснена
 * из "горячего" кеша (Redis). Система обязана выполнить отказоустойчивую проверку (fallback), обратившись к персистентному
 * хранилищу (БД), чтобы гарантированно идентифицировать дубликат и предотвратить повторное списание средств.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Отказоустойчивая идемпотентность (Cache-Fallback Idempotency):</b>
 *     <p><b>Что проверяем:</b> Реакцию системы на дубликат запроса, идентификатор транзакции которого отсутствует в Redis, но присутствует в базе данных.
 *     <p><b>Почему это важно:</b> Кеши по своей природе являются эфемерными. Система, полагающаяся исключительно на кеш для
 *     обеспечения идемпотентности, является хрупкой и уязвимой для двойных списаний в долгосрочной перспективе. Этот тест
 *     доказывает наличие отказоустойчивого fallback-механизма к источнику истины (БД), что является критическим требованием
 *     для обеспечения семантики обработки "exactly-once" в финансовых системах.
 *   </li>
 *   <li><b>Корректность идемпотентного ответа:</b>
 *     <p><b>Что проверяем:</b> Возврат успешного статуса {@link HttpStatus#OK} и тела ответа с нулевым балансом.
 *     <p><b>Почему это важно:</b> Успешный статус необходим для корректной интеграции с клиентами (игровыми провайдерами),
 *     сигнализируя об успешной обработке запроса без необходимости повторных попыток. Нулевой баланс в теле ответа
 *     служит индикатором того, что операция была распознана как дубликат и нового списания не произошло.
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Создается игрок и игровая сессия.</li>
 *   <li>Выполняется {@code N+1} ставок, чтобы гарантированно вытеснить первую ставку из кеша Redis, лимит которого равен N.</li>
 *   <li>Путем сравнения списка всех совершенных транзакций и списка транзакций в Redis определяется идентификатор вытесненной транзакции.</li>
 *   <li>Отправляется повторный запрос с идентификатором вытесненной транзакции.</li>
 *   <li>Анализируется полученный ответ.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает статус {@link HttpStatus#OK}.</li>
 *   <li>Тело ответа содержит идентификатор исходной (вытесненной) транзакции.</li>
 *   <li>Баланс в теле ответа равен {@link BigDecimal#ZERO}.</li>
 *   <li>Финальный баланс игрока в системе не изменяется после отправки дубликата.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class DuplicateDisplacedBetParametrizedTest extends BaseParameterizedTest {


    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal DEFAULT_BET_AMOUNT = new BigDecimal("1.00");

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
    @DisplayName("Дублирование ставки, вытесненной из кеша")
    void testDuplicateDisplacedBetReturnsIdempotentResponse(NatsGamblingTransactionOperation operationParam, BigDecimal betAmountParam) {
        final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
        final int maxGamblingCountInRedis = 50;

        final int betsToMakeToDisplace = maxGamblingCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<BetRequestBody> allMadeBetRequests = new ArrayList<>();
            String lastMadeBetTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastBetNatsEvent;
            BetRequestBody displacedBetRequestToDuplicate;
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

        step("Manager API: Совершение ставок для вытеснения (тип: " + operationParam + ", сумма: " + betAmountParam + ")", () -> {
            for (int i = 0; i < betsToMakeToDisplace; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == betsToMakeToDisplace - 1) {
                    ctx.lastMadeBetTransactionId = transactionId;
                }
                var betRequestBody = BetRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                        .amount(betAmountParam)
                        .transactionId(transactionId)
                        .type(operationParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(false)
                        .build();
                ctx.allMadeBetRequests.add(betRequestBody);

                var currentBetNumber = i + 1;
                step("Совершение ставки #" + currentBetNumber + " (ID: " + transactionId + ")", () -> {
                    var response = managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, betRequestBody),
                            betRequestBody);
                    assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");
                });
            }
        });

        step("NATS: Ожидание NATS-события betted_from_gamble для последней ставки", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());
            ctx.lastBetNatsEvent = natsClient.expect(NatsGamblingEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
                    .with("$.uuid", ctx.lastMadeBetTransactionId)
                    .fetch();

            assertNotNull(ctx.lastBetNatsEvent, "nats.betted_from_gamble");
        });

        step("Redis: Определение вытесненной ставки", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.lastBetNatsEvent.getSequence())
                    .fetch();
            var transactionIdsCurrentlyInRedis = aggregate.gambling().keySet();

            var allMadeTransactionIds = ctx.allMadeBetRequests.stream()
                    .map(BetRequestBody::getTransactionId)
                    .collect(Collectors.toCollection(HashSet::new));

            allMadeTransactionIds.removeAll(transactionIdsCurrentlyInRedis);
            assertEquals(1, allMadeTransactionIds.size(), "redis.displaced_transaction.count");
            var displacedTxId = allMadeTransactionIds.iterator().next();

            ctx.displacedBetRequestToDuplicate = ctx.allMadeBetRequests.stream()
                    .filter(betReq -> betReq.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));
        });

        step("Manager API: Попытка дублирования вытесненной ставки", () -> {
            var duplicateResponse = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.displacedBetRequestToDuplicate),
                    ctx.displacedBetRequestToDuplicate
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("manager_api.duplicate_displaced_bet.response",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.status_code"),
                    () -> assertNotNull(responseBody, "manager_api.response_body"),
                    () -> assertEquals(ctx.displacedBetRequestToDuplicate.getTransactionId(), responseBody.transactionId(), "manager_api.transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.balance()), "manager_api.balance")
            );
        });
    }
}
