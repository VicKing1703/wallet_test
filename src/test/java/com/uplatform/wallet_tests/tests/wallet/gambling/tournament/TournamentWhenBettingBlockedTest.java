package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
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
 * Проверяет, что начисление турнирного выигрыша доступно при заблокированном беттинге.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что локальная блокировка беттинга не влияет на турнирные выплаты, а Manager API
 * продолжает обрабатывать запросы {@code POST /tournament} с ожидаемым результатом.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Корректность блокировки:</b>
 *     <p><b>Что проверяем:</b> Ответ CAP API при отключении беттинга для игрока.</p>
 *     <p><b>Почему это важно:</b> Блокировка должна успешно применяться, чтобы подтвердить стартовые условия теста.</p>
 *   </li>
 *   <li><b>Доступность турнирного выигрыша:</b>
 *     <p><b>Что проверяем:</b> Ответ Manager API на начисление турнира после блокировки.</p>
 *     <p><b>Почему это важно:</b> Турнирные выигрыши не должны зависеть от статуса беттинга.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <ol>
 *   <li>Зарегистрировать игрока и создать игровую сессию.</li>
 *   <li>Заблокировать беттинг через CAP API.</li>
 *   <li>Начислить турнирный выигрыш и проверить успешный ответ.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>CAP API возвращает {@link HttpStatus#NO_CONTENT} при установке блокировки.</li>
 *   <li>Manager API возвращает {@link HttpStatus#OK} при начислении выигрыша.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Позитивные сценарии: /tournament")
@Tag("Gambling")
@Tag("Wallet")
class TournamentWhenBettingBlockedTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal TOURNAMENT_AMOUNT = new BigDecimal("50.25");

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    @Test
    @DisplayName("Получение выигрыша в турнире игроком с заблокированным беттингом")
    void shouldAwardTournamentWinAndVerify() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии и проверка в БД", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("CAP API: Блокировка беттинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(true)
                    .bettingEnabled(false)
                    .build();

            var response = capAdminClient.updateBlockers(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap.update_blockers.status_code");
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            var request = TournamentRequestBody.builder()
                    .amount(TOURNAMENT_AMOUNT)
                    .playerId(ctx.registeredPlayer.walletData().walletUUID())
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(ctx.gameLaunchData.dbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(ctx.gameLaunchData.dbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, request),
                    request);

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code")
            );
        });
    }
}