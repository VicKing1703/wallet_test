package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
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

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Проверяет отказ системы при рефанде турнирного выигрыша.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что идентификатор турнирного выигрыша нельзя использовать как ставку для рефанда.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Турнирный выигрыш:</b>
 *     <p><b>Что проверяем:</b> успешное начисление через {@code /tournament}.</p>
 *     <p><b>Почему это важно:</b> корректность исходной операции гарантирует валидность негативной проверки.</p>
 *   </li>
 *   <li><b>Рефанд:</b>
 *     <p><b>Что проверяем:</b> ошибку {@link GamblingErrors#REFUND_NOT_ALLOWED}.</p>
 *     <p><b>Почему это важно:</b> защищает бизнес-логику от повторных выплат.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Турнирный выигрыш проходит успешно.</li>
 *   <li>Рефанд завершается ошибкой {@code 400 BAD REQUEST}.</li>
 *   <li>Код и сообщение ошибки соответствуют {@code REFUND_NOT_ALLOWED}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Негативные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundAfterTournamentTest extends BaseTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
    private static final BigDecimal TOURNAMENT_AMOUNT = generateBigDecimalAmount(INITIAL_ADJUSTMENT_AMOUNT);

    private String casinoId;

    @BeforeEach
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());
    }

    /**
     * Имитирует начисление турнирного выигрыша и проверяет невозможность рефанда.
     *
     * <p><b>Идея теста:</b>
     * После успешного начисления попытаться вернуть средства через {@code /refund}.</p>
     *
     * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
     * <ul>
     *   <li><b>Tournament API:</b>
     *     <p><b>Что проверяем:</b> статус {@code 200 OK} и корректность тела ответа.</p>
     *     <p><b>Почему это важно:</b> базовая операция выигрыша должна работать без ошибок.</p>
     *   </li>
     *   <li><b>Refund API:</b>
     *     <p><b>Что проверяем:</b> ошибку {@code REFUND_NOT_ALLOWED} при попытке вернуть турнирный выигрыш.</p>
     *     <p><b>Почему это важно:</b> исключает повторную выплату средств.</p>
     *   </li>
     * </ul>
     *
     * <p><b>Ожидаемые результаты:</b></p>
     * <ul>
     *   <li>Начисление проходит успешно.</li>
     *   <li>Рефанд завершает работу с ошибкой {@code 400 BAD REQUEST}.</li>
     *   <li>Ответ содержит код и сообщение {@code REFUND_NOT_ALLOWED}.</li>
     * </ul>
     */
    @Test
    @DisplayName("Рефанд для турнирного выигрыша должен быть отклонен")
    void test() {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequest;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            ctx.tournamentRequest = TournamentRequestBody.builder()
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
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.tournamentRequest),
                    ctx.tournamentRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code");
        });

        step("Manager API: Попытка выполнения рефанда для турнирного выигрыша", () -> {
            var request = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
                    .amount(TOURNAMENT_AMOUNT)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.tournamentRequest.getTransactionId())
                    .roundId(ctx.tournamentRequest.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.refund(
                            casinoId,
                            utils.createSignature(ApiEndpoints.REFUND, request),
                            request
                    ),
                    "manager_api.refund_after_tournament.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("manager_api.refund.after_tournament.error_validation",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.refund.status_code"),
                    () -> assertNotNull(error, "manager_api.refund.body"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getCode(), error.code(), "manager_api.refund.error_code"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getMessage(), error.message(), "manager_api.refund.error_message")
            );
        });
    }
}