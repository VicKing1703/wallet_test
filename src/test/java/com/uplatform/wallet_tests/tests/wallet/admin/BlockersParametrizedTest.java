package com.uplatform.wallet_tests.tests.wallet.admin;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsPreventGambleSettedPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный параметризованный тест, проверяющий обновление блокировок на гемблинг и беттинг для игрока через CAP API.
 *
 * <p>Тест эмулирует действия администратора, который устанавливает или снимает ограничения на игровые активности игрока.
 * Проверяется вся цепочка распространения изменений: от API-запроса до обновления данных в NATS, Kafka, основной базе данных и Redis,
 * а также корректное отображение нового статуса при повторном запросе к API.</p>
 *
 * <p><b>Последовательность действий для каждого набора параметров:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока.</li>
 *   <li>Отправка запроса в CAP API для обновления флагов {@code gamblingEnabled} и {@code bettingEnabled}.</li>
 *   <li>Прослушивание и валидация NATS-события типа {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType#SETTING_PREVENT_GAMBLE_SETTED},
 *       подтверждающего изменение настроек.</li>
 *   <li>Проверка того, что NATS-событие было успешно спроецировано в Kafka-топик {@code wallet.v8.projectionSource}.</li>
 *   <li>Проверка прямого изменения флагов в основной базе данных кошелька (таблица wallet).</li>
 *   <li>Проверка обновления состояния кошелька в Redis.</li>
 *   <li>Отправка запроса в CAP API для получения текущих блокировок и проверка их соответствия установленным значениям.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос на обновление блокировок выполняется успешно (HTTP 204 NO CONTENT).</li>
 *   <li>В NATS публикуется событие {@code setting_prevent_gamble_setted} с корректными флагами.</li>
 *   <li>Событие из NATS полностью и корректно дублируется в проекционный топик Kafka {@code wallet.v8.projectionSource}.</li>
 *   <li>Флаги {@code is_gambling_active} и {@code is_betting_active} в базе данных обновляются.</li>
 *   <li>Состояние кошелька в Redis отражает новые настройки блокировок.</li>
 *   <li>Запрос на получение блокировок через CAP API возвращает актуальные значения.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Управление игроком")
@Suite("Ручные блокировки гемблинга и беттинга: Позитивные сценарии")
@Tag("Wallet") @Tag("CAP")
class BlockersParametrizedTest extends BaseParameterizedTest {

    private String platformNodeId;

    @BeforeAll
    void setupGlobalTestContext() {
        this.platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
    }

    static Stream<Arguments> blockersProvider() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false)
        );
    }

    @ParameterizedTest(name = "Gambling={0}, Betting={1}")
    @MethodSource("blockersProvider")
    @DisplayName("Обновление блокировок гемблинга и беттинга:")
    void shouldUpdatePlayerBlockersAndVerifyEvents(
            boolean gamblingEnabled,
            boolean bettingEnabled
    ) {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            UpdateBlockersRequest updateBlockersRequest;
            NatsMessage<NatsPreventGambleSettedPayload> updateBlockersEvent;
            WalletProjectionMessage walletProjectionMessage;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(ctx.registeredPlayer.walletData(), "default_step.registration.wallet_data");
        });

        step("CAP API: Обновление блокировок гемблинга и беттинга", () -> {
            ctx.updateBlockersRequest = UpdateBlockersRequest.builder()
                    .gamblingEnabled(gamblingEnabled)
                    .bettingEnabled(bettingEnabled)
                    .build();

            var response = capAdminClient.updateBlockers(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    ctx.updateBlockersRequest
            );
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_blockers.status_code");
        });

        step("NATS: Проверка события setting_prevent_gamble_setted", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID()
            );

            ctx.updateBlockersEvent = natsClient.expect(NatsPreventGambleSettedPayload.class)
                    .from(subject)
                    .withType(NatsEventType.SETTING_PREVENT_GAMBLE_SETTED.getHeaderValue())
                    .with("is_gambling_active", gamblingEnabled)
                    .with("is_betting_active", bettingEnabled)
                    .unique()
                    .fetch();

            var payload = ctx.updateBlockersEvent.getPayload();
            assertAll("Проверка полей NATS-события",
                    () -> assertEquals(gamblingEnabled, payload.gamblingActive(), "nats.setting_prevent_gamble_setted.is_gambling_active"),
                    () -> assertEquals(bettingEnabled, payload.bettingActive(), "nats.setting_prevent_gamble_setted.is_betting_active"),
                    () -> assertNotNull(payload.createdAt(), "nats.setting_prevent_gamble_setted.created_at")
            );
        });

        step("Kafka: Проверка события setting_prevent_gamble_setted в топике wallet.v8.projectionSource", () -> {
            ctx.walletProjectionMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("type", ctx.updateBlockersEvent.getType())
                    .with("seq_number", ctx.updateBlockersEvent.getSequence())
                    .unique()
                    .fetch();

            var kafkaMessage = ctx.walletProjectionMessage;
            var expectedPayload = assertDoesNotThrow(() ->
                    objectMapper.writeValueAsString(ctx.updateBlockersEvent.getPayload()));

            assertAll("Проверка полей Kafka-сообщения, спроецированного из NATS",
                    () -> assertEquals(ctx.updateBlockersEvent.getType(), kafkaMessage.type(), "kafka.setting_prevent_gamble_setted.type"),
                    () -> assertEquals(ctx.updateBlockersEvent.getSequence(), kafkaMessage.seqNumber(), "kafka.setting_prevent_gamble_setted.seq_number"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().walletUUID(), kafkaMessage.walletUuid(), "kafka.setting_prevent_gamble_setted.wallet_uuid"),
                    () -> assertEquals(expectedPayload, kafkaMessage.payload(), "kafka.setting_prevent_gamble_setted.payload")
            );
        });

        step("DB (Wallet): Проверка флагов в таблице wallet", () -> {
            var wallet = walletDatabaseClient.findWalletByUuidOrFail(
                    ctx.registeredPlayer.walletData().walletUUID()
            );
            assertAll("Проверка состояния флагов в основной БД",
                    () -> assertEquals(gamblingEnabled, wallet.isGamblingActive(), "db.wallet.is_gambling_active"),
                    () -> assertEquals(bettingEnabled, wallet.isBettingActive(), "db.wallet.is_betting_active")
            );
        });

        step("Redis (Wallet): Проверка состояния кошелька", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", ctx.updateBlockersEvent.getSequence())
                    .fetch();
            assertAll("Проверка флагов в агрегате Redis",
                    () -> assertEquals(gamblingEnabled, aggregate.isGamblingActive(), "redis.wallet.is_gambling_active"),
                    () -> assertEquals(bettingEnabled, aggregate.isBettingActive(), "redis.wallet.is_betting_active")
            );
        });

        step("CAP API: Проверка получения обновленных блокировок", () -> {
            var response = capAdminClient.getBlockers(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.get_blockers.status_code");
            assertNotNull(response.getBody(), "cap_api.get_blockers.response_body");

            assertAll("Проверка данных в ответе getBlockers",
                    () -> assertEquals(gamblingEnabled, response.getBody().gamblingEnabled(), "cap_api.get_blockers.gambling_enabled"),
                    () -> assertEquals(bettingEnabled, response.getBody().bettingEnabled(), "cap_api.get_blockers.betting_enabled")
            );
        });
    }
}