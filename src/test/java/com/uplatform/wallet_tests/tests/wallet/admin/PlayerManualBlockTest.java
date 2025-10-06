package com.uplatform.wallet_tests.tests.wallet.admin;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.http.cap.dto.update_player_properties.UpdatePlayerPropertiesRequest;
import com.uplatform.wallet_tests.api.kafka.dto.player_status.PlayerStatusUpdateMessage;
import com.uplatform.wallet_tests.api.kafka.dto.player_status.enums.PlayerAccountEventType;
import com.uplatform.wallet_tests.api.kafka.dto.player_status.enums.PlayerAccountStatus;
import com.uplatform.wallet_tests.api.nats.dto.NatsWalletBlockedPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий процесс ручной блокировки игрока через CAP API
 * и последующее распространение информации о блокировке по различным компонентам системы.
 *
 * <p>Тест эмулирует действие администратора, который вручную блокирует аккаунт игрока.
 * Проверяется, что после вызова соответствующего эндпоинта CAP API система корректно
 * обновляет статус игрока и генерирует необходимые события для оповещения других
 * микросервисов. Это подтверждает целостность и согласованность данных в распределенной среде.</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока для получения уникальных идентификаторов.</li>
 *   <li>Отправка запроса в CAP API для обновления свойств игрока с установкой флага {@code manuallyBlocked = true}.</li>
 *   <li>Прослушивание и валидация Kafka-сообщения в топике {@code player.v1.account}, событие типа {@link com.uplatform.wallet_tests.api.kafka.dto.player_status.enums.PlayerAccountEventType#PLAYER_STATUS_UPDATE},
 *       подтверждающего смену статуса игрока на {@link com.uplatform.wallet_tests.api.kafka.dto.player_status.enums.PlayerAccountStatus#BLOCKED}.</li>
 *   <li>Прослушивание и валидация NATS-события типа {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType#WALLET_BLOCKED},
 *       указывающего на блокировку кошелька.</li>
 *   <li>Проверка того, что NATS-событие было успешно спроецировано в Kafka-топик {@code wallet.v8.projectionSource} с тем же типом {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType#WALLET_BLOCKED}.</li>
 *   <li>Проверка конечного состояния в Redis, чтобы убедиться, что данные кошелька (агрегат) отражают статус блокировки.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос на блокировку игрока через CAP API выполняется успешно (HTTP 204 NO CONTENT).</li>
 *   <li>В Kafka топике {@code player.v1.account} появляется событие типа {@link com.uplatform.wallet_tests.api.kafka.dto.player_status.enums.PlayerAccountEventType#PLAYER_STATUS_UPDATE} о смене статуса игрока на {@code BLOCKED}.</li>
 *   <li>В NATS публикуется событие типа {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType#WALLET_BLOCKED} о блокировке кошелька.</li>
 *   <li>Данные из NATS-события корректно дублируются в проекционный топик Kafka {@code wallet.v8.projectionSource} с тем же типом события.</li>
 *   <li>Состояние кошелька в Redis обновляется, и флаг {@code isBlocked} устанавливается в {@code true}.</li>
 *   <li>Все ожидаемые события в Kafka и NATS являются уникальными в рамках теста, что гарантирует отсутствие дублирования сообщений.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("PlayerBlockers")
@Suite("Позитивные сценарии: PlayerProperties")
@Tag("Wallet") @Tag("CAP")
class PlayerManualBlockTest extends BaseTest {

    @Test
    @DisplayName("CAP: Блокировка игрока обновляет статус и отправляет события")
    void shouldBlockPlayerAndPublishEvents() {
        final String PLATFORM_NODE_ID = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String PLATFORM_USER_ID = HttpServiceHelper.getCapPlatformUserId(configProvider.getEnvironmentConfig().getHttp());
        final String PLATFORM_USERNAME = HttpServiceHelper.getCapPlatformUsername(configProvider.getEnvironmentConfig().getHttp());

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            UpdatePlayerPropertiesRequest updateRequest;
            PlayerStatusUpdateMessage statusUpdateMessage;
            NatsMessage<NatsWalletBlockedPayload> walletBlockedEvent;
            WalletProjectionMessage walletProjectionMessage;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(ctx.registeredPlayer.walletData(), "default_step.registration.wallet_data");
        });

        step("CAP API: Установка флага ручной блокировки игрока", () -> {
            ctx.updateRequest = UpdatePlayerPropertiesRequest.builder()
                    .manuallyBlocked(true)
                    .blockDeposit(false)
                    .blockWithdrawal(false)
                    .blockGambling(false)
                    .blockBetting(false)
                    .build();

            var response = capAdminClient.updatePlayerProperties(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    PLATFORM_NODE_ID,
                    PLATFORM_USER_ID,
                    PLATFORM_USERNAME,
                    ctx.updateRequest
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_player_properties.status_code");
        });

        step("Kafka: Проверка события player.statusUpdate", () -> {
            ctx.statusUpdateMessage = kafkaClient.expect(PlayerStatusUpdateMessage.class)
                    .with("message.eventType", PlayerAccountEventType.PLAYER_STATUS_UPDATE.getValue())
                    .with("player.externalId", ctx.registeredPlayer.walletData().playerUUID())
                    .with("player.status", PlayerAccountStatus.BLOCKED.getCode())
                    .unique()
                    .fetch();

            assertAll(
                    () -> assertEquals(PlayerAccountEventType.PLAYER_STATUS_UPDATE, ctx.statusUpdateMessage.message().eventType(),
                            "kafka.player_status_update.event_type"),
                    () -> assertNotNull(ctx.statusUpdateMessage.message().eventCreatedAt(),
                            "kafka.player_status_update.event_created_at"),
                    () -> assertNotNull(ctx.statusUpdateMessage.player(), "kafka.player_status_update.player"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(),
                            ctx.statusUpdateMessage.player().externalId(), "kafka.player_status_update.external_id"),
                    () -> assertFalse(ctx.statusUpdateMessage.player().activeStatus(),
                            "kafka.player_status_update.active_status"),
                    () -> assertEquals(PlayerAccountStatus.BLOCKED, ctx.statusUpdateMessage.player().status(),
                            "kafka.player_status_update.status")
            );
        });

        step("NATS: Проверка события wallet_blocked", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID()
            );

            ctx.walletBlockedEvent = natsClient.expect(NatsWalletBlockedPayload.class)
                    .from(subject)
                    .withType(NatsEventType.WALLET_BLOCKED.getHeaderValue())
                    .unique()
                    .fetch();

            assertTrue(ctx.walletBlockedEvent.getPayload().date() >= 0, "nats.wallet_blocked.date.non_negative");
        });

        step("Kafka: Проверка события wallet_blocked в топике wallet.v8.projectionSource", () -> {
            ctx.walletProjectionMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("type", ctx.walletBlockedEvent.getType())
                    .with("seq_number", ctx.walletBlockedEvent.getSequence())
                    .unique()
                    .fetch();

            var kafkaMessage = ctx.walletProjectionMessage;
            var expectedPayload = assertDoesNotThrow(() ->
                            objectMapper.writeValueAsString(ctx.walletBlockedEvent.getPayload()));
            var expectedTimestamp = ctx.walletBlockedEvent.getTimestamp().toEpochSecond();

            assertAll(
                    () -> assertEquals(ctx.walletBlockedEvent.getType(), kafkaMessage.type(),
                            "kafka.wallet_blocked.type"),
                    () -> assertEquals(ctx.walletBlockedEvent.getSequence(), kafkaMessage.seqNumber(),
                            "kafka.wallet_blocked.seq_number"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().walletUUID(), kafkaMessage.walletUuid(),
                            "kafka.wallet_blocked.wallet_uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), kafkaMessage.playerUuid(),
                            "kafka.wallet_blocked.player_uuid"),
                    () -> assertEquals(PLATFORM_NODE_ID, kafkaMessage.nodeUuid(),
                            "kafka.wallet_blocked.node_uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), kafkaMessage.currency(),
                            "kafka.wallet_blocked.currency"),
                    () -> assertEquals(expectedPayload, kafkaMessage.payload(),
                            "kafka.wallet_blocked.payload"),
                    () -> assertEquals(expectedTimestamp, kafkaMessage.timestamp(),
                            "kafka.wallet_blocked.timestamp"),
                    () -> assertNotNull(kafkaMessage.seqNumberNodeUuid(),
                            "kafka.wallet_blocked.seq_number_node_uuid")
            );
        });

        step("Redis (Wallet): Проверка статуса кошелька", () -> {
            assertNotNull(ctx.walletBlockedEvent, "context.wallet_blocked_event");

            var walletUuid = ctx.registeredPlayer.walletData().walletUUID();
            var walletAggregate = redisWalletClient
                    .key(walletUuid)
                    .withAtLeast("LastSeqNumber", ctx.walletBlockedEvent.getSequence())
                    .fetch();

            assertTrue(walletAggregate.isBlocked(), "redis.wallet.is_blocked");
        });
    }
}