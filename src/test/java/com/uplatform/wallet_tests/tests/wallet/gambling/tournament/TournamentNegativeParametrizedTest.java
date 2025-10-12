package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;

import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrorMessages;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.tests.base.BaseNegativeParameterizedTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Проверяет обработку некорректных запросов на начисление турнирного выигрыша.
 *
 * <p><b>Идея теста:</b>
 * Убедиться, что {@code POST /tournament} строго валидирует входящие данные и не изменяет состояние кошелька
 * при некорректных параметрах запроса.</p>
 *
 * <p><b>Ключевые аспекты проверки (Что и почему):</b></p>
 * <ul>
 *   <li><b>Наличие обязательных атрибутов:</b>
 *     <p><b>Что проверяем:</b> Реакцию API на отсутствие или пустые значения {@code sessionToken}, {@code transactionId}
 *     и {@code roundId}.</p>
 *     <p><b>Почему это важно:</b> Без этих идентификаторов нельзя корректно связать транзакции между сервисами.</p>
 *   </li>
 *   <li><b>Валидация значений:</b>
 *     <p><b>Что проверяем:</b> Проверку диапазона суммы и формата UUID.</p>
 *     <p><b>Почему это важно:</b> Некорректные значения должны блокироваться до выполнения бизнес-логики.</p>
 *   </li>
 * </ul>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>API возвращает ожидаемые коды {@link GamblingErrors} и сообщения {@link GamblingErrorMessages}.</li>
 *   <li>Баланс игрока остается неизменным.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Негативные сценарии: /tournament")
@Tag("Gambling")
@Tag("Wallet7")
class TournamentNegativeParametrizedTest extends BaseNegativeParameterizedTest {

    private static final BigDecimal INITIAL_ADJUSTMENT_AMOUNT = new BigDecimal("20.00");
    private static final BigDecimal VALID_TOURNAMENT_AMOUNT = new BigDecimal("10.00");

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private String casinoId;

    @BeforeAll
    void setUp() {
        casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_ADJUSTMENT_AMOUNT);
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
        });
    }

    static Stream<Arguments> negativeTournamentScenariosProvider() {
        return Stream.of(
                Arguments.of("без sessionToken",
                        (Consumer<TournamentRequestBody>) req -> req.setSessionToken(null),
                        GamblingErrors.MISSING_TOKEN,
                        GamblingErrorMessages.MISSING_SESSION_TOKEN
                ),
                Arguments.of("пустой sessionToken",
                        (Consumer<TournamentRequestBody>) req -> req.setSessionToken(""),
                        GamblingErrors.MISSING_TOKEN,
                        GamblingErrorMessages.MISSING_SESSION_TOKEN
                ),
                Arguments.of("отрицательный amount",
                        (Consumer<TournamentRequestBody>) req -> req.setAmount(new BigDecimal("-1.00")),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.AMOUNT_NEGATIVE
                ),
                Arguments.of("без transactionId",
                        (Consumer<TournamentRequestBody>) req -> req.setTransactionId(null),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_BLANK
                ),
                Arguments.of("пустой transactionId",
                        (Consumer<TournamentRequestBody>) req -> req.setTransactionId(""),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_BLANK
                ),
                Arguments.of("невалидный transactionId (не UUID)",
                        (Consumer<TournamentRequestBody>) req -> req.setTransactionId("not-a-uuid"),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.TRANSACTION_ID_INVALID_UUID
                ),
                Arguments.of("roundId превышает 255 символов",
                        (Consumer<TournamentRequestBody>) req -> req.setRoundId("a".repeat(256)),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_TOO_LONG
                ),
                Arguments.of("без roundId",
                        (Consumer<TournamentRequestBody>) req -> req.setRoundId(null),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_BLANK
                ),
                Arguments.of("пустой roundId",
                        (Consumer<TournamentRequestBody>) req -> req.setRoundId(""),
                        GamblingErrors.VALIDATION_ERROR,
                        GamblingErrorMessages.ROUND_ID_BLANK
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeTournamentScenariosProvider")
    @DisplayName("Негативный сценарий получения выигрыша в турнире:")
    void tournamentNegativeTest(
            String description,
            Consumer<TournamentRequestBody> requestModifier,
            GamblingErrors expectedErrorCode,
            String expectedMessage
    ) {
        final class TestContext {
            TournamentRequestBody request;
            GamblingError error;
        }
        final TestContext ctx = new TestContext();

        step("Подготовка некорректного запроса: " + description, () -> {
            ctx.request = TournamentRequestBody.builder()
                    .amount(VALID_TOURNAMENT_AMOUNT)
                    .playerId(registeredPlayer.walletData().playerUUID())
                    .sessionToken(gameLaunchData.dbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(gameLaunchData.dbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(gameLaunchData.dbGameSession().getProviderUuid())
                    .build();

            requestModifier.accept(ctx.request);
        });

        ctx.error = step(
                "Manager API: Попытка некорректного начисления турнирного выигрыша - " + description,
                () -> executeExpectingError(
                        () -> managerClient.tournament(
                                casinoId,
                                utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.request),
                                ctx.request
                        ),
                        "manager_api.tournament.expected_exception",
                        GamblingError.class
                )
        );

        step("Проверка структуры ошибки", () ->
                assertValidationError(
                        ctx.error,
                        expectedErrorCode.getCode(),
                        expectedMessage
                )
        );
    }
}
