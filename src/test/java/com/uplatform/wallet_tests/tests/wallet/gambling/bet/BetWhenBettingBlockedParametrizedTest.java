package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
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
 * Интеграционный тест, верифицирующий селективность механизма блокировок при совершении ставки в казино.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить корректность и изоляцию механизмов блокировки для различных игровых вертикалей.
 * Система должна четко разграничивать ограничения, наложенные на "гемблинг" (казино-игры) и "беттинг" (ставки на спорт).
 * Данный тест должен доказать, что активация блокировки для одной вертикали ({@code bettingEnabled=false}) не оказывает
 * нежелательного влияния на операции в другой, разрешенной вертикали ({@code gamblingEnabled=true}). Это гарантирует
 * гибкость и точность системы управления ограничениями игрока.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Селективность блокировки:</b>
 *     <p><b>Что проверяем:</b> Возможность успешно выполнить транзакцию в казино (через {@code POST /bet}) для игрока,
 *     у которого активна блокировка на спортивные ставки ({@code bettingEnabled=false}).
 *     <p><b>Почему это важно:</b> Это подтверждает, что бизнес-логика системы корректно интерпретирует и применяет
 *     различные типы ограничений. Неправильная, чрезмерно широкая блокировка привела бы к необоснованному отказу в
 *     обслуживании и прямым финансовым потерям, а также свидетельствовала бы о серьезном дефекте в логике
 *     применения пользовательских ограничений.
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API-запрос на совершение ставки в казино успешно обрабатывается (HTTP 200 OK).</li>
 *   <li>В ответе API возвращается корректный идентификатор транзакции, подтверждая ее успешное выполнение.</li>
 *   <li>Система не возвращает никаких ошибок, связанных с блокировкой игрока.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Позитивные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class BetWhenBettingBlockedParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal BET_AMOUNT = new BigDecimal("10.00");

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;

    @BeforeAll
    void setUp() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
        });

        step("CAP API: Блокировка беттинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(true)
                    .bettingEnabled(false)
                    .build();

            var response = capAdminClient.updateBlockers(
                    registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_blockers.status_code");
        });
    }

    static Stream<Arguments> blockedBetProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.BET),
                Arguments.of(NatsGamblingTransactionOperation.TIPS),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN)
        );
    }

    /**
     * @param type Тип операции ставки для проверки
     */
    @ParameterizedTest(name = "тип = {0}")
    @MethodSource("blockedBetProvider")
    @DisplayName("Совершение ставок игроком с заблокированным беттингом:")
    void test(
            NatsGamblingTransactionOperation type
    ) {
        final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        step("Manager API: Совершение ставки", () -> {
            var request = BetRequestBody.builder()
                    .sessionToken(gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(BET_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .type(type)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, request),
                    request);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.bet.body_not_null"),
                    () -> assertEquals(request.getTransactionId(), response.getBody().transactionId(), "manager_api.bet.transactionId")
            );
        });
    }
}