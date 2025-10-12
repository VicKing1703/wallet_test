package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий получение выигрыша при заблокированном гемблинге.
 *
 * <p><b>Идея теста:</b>
 * Подтвердить, что блокировка гемблинга запрещает новые ставки, но не препятствует выплате выигрышей
 * за уже совершенные раунды. Игрок должен сохранить возможность получить причитающиеся средства.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Применение блокировки:</b>
 *     <p><b>Что проверяем:</b> CAP API корректно устанавливает состояние
 *     {@code gamblingEnabled=false}/{@code bettingEnabled=true}.</p>
 *     <p><b>Почему это важно:</b> Неконсистентные флаги приведут к ошибочному поведению веб-интерфейса и API.</p>
 *   </li>
 *   <li><b>Доступность выигрыша:</b>
 *     <p><b>Что проверяем:</b> Ответ Manager API — статус {@link HttpStatus#OK} и положительный баланс
 *     в теле {@link com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody}.</p>
 *     <p><b>Почему это важно:</b> Игрок должен получать деньги независимо от текущего статуса блокировок.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>CAP API возвращает {@link HttpStatus#NO_CONTENT} при установке блокировки.</li>
 *   <li>Каждый запрос {@code /win} завершается успешно и возвращает исходный {@code transactionId}.</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/win")
@Suite("Позитивные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class WinWhenGamblingBlockedParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("100.00");

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private String casinoId;

    @BeforeAll
    void setUp() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
        });

        step("CAP API: Блокировка гемблинга (gamblingEnabled=false)", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(false)
                    .bettingEnabled(true)
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

    static Stream<Arguments> blockedWinProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.WIN),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT)
        );
    }

    /**
     * @param type Тип операции выигрыша для проверки (WIN, FREESPIN, JACKPOT)
     */
    @ParameterizedTest(name = "тип = {0}")
    @MethodSource("blockedWinProvider")
    @DisplayName("Получение выигрыша игроком с заблокированным гемблингом:")
    void test(NatsGamblingTransactionOperation type) {
        final class TestContext {
            WinRequestBody request;
            String transactionId;
            BigDecimal winAmount;
        }
        final TestContext ctx = new TestContext();

        ctx.winAmount = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);

        step("Manager API: Получение выигрыша типа " + type, () -> {
            ctx.transactionId = UUID.randomUUID().toString();

            ctx.request = WinRequestBody.builder()
                    .sessionToken(gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(ctx.winAmount)
                    .transactionId(ctx.transactionId)
                    .type(type)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(true)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.request),
                    ctx.request);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.win.body_not_null"),
                    () -> assertEquals(ctx.transactionId, response.getBody().transactionId(), "manager_api.win.transactionId"),
                    () -> assertTrue(response.getBody().balance().compareTo(BigDecimal.ZERO) > 0, "manager_api.win.balance")
            );
        });
    }
}
