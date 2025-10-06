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
 * Интеграционный тест, проверяющий, что после снятия ручной блокировки игрока
 * статус и агрегаты во всех задействованных компонентах системы возвращаются
 * в активное состояние.
 *
 * <p>Сценарий основывается на последовательности из теста {@link PlayerManualBlockTest}:
 * сначала администратор вручную блокирует игрока через CAP API, после чего
 * выполняется дополнительный шаг по снятию блокировки. В рамках текущего теста
 * кросс-системные проверки (Kafka, NATS, Redis) выполняются <b>только</b> после
 * разблокировки, что соответствует требованиям к регрессионной проверке.</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока и подготовка контекста теста.</li>
 *   <li>Вызов CAP API для установки флага {@code manuallyBlocked = true} (без
 *       дополнительных проверок в Kafka/NATS/Redis на этом этапе).</li>
 *   <li>Повторный вызов CAP API с флагом {@code manuallyBlocked = false} для снятия блокировки.</li>
 *   <li>Ожидание и валидация Kafka-события {@code player.statusUpdate} с переходом
 *       статуса игрока в {@link PlayerAccountStatus#ACTIVE}.</li>
 *   <li>Проверка NATS-события типа {@link NatsEventType#WALLET_BLOCKED}, подтверждающего
 *       обновление последовательности событий кошелька после разблокировки.</li>
 *   <li>Проверка, что NATS-событие корректно попало в Kafka-топик
 *       {@code wallet.v8.projectionSource} и содержит консистентные данные.</li>
 *   <li>Валидация итогового состояния кошелька в Redis: флаг {@code isBlocked}
 *       сброшен, последовательность {@code LastSeqNumber} соответствует событию разблокировки.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Оба запроса CAP API завершаются кодом {@code 204 NO CONTENT}.</li>
 *   <li>В Kafka фиксируется событие со статусом {@code ACTIVE} для разблокированного игрока.</li>
 *   <li>В NATS появляется новое событие типа {@code wallet_blocked}, связанное с снятием блокировки.</li>
 *   <li>Kafka-проекция {@code wallet.v8.projectionSource} отражает метаданные NATS-события.</li>
 *   <li>Redis-агрегат кошелька показывает, что блокировка снята и последовательность обновлена.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("PlayerBlockers")
@Suite("Позитивные сценарии: PlayerProperties")
@Tag("Wallet")
@Tag("CAP")
class PlayerManualUnblockTest extends BaseTest {

    @Test
    @DisplayName("CAP: Снятие ручной блокировки обновляет статус и отправляет события")
    void shouldUnblockPlayerAndPublishEvents() {
        final String PLATFORM_NODE_ID = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String PLATFORM_USER_ID = HttpServiceHelper.getCapPlatformUserId(configProvider.getEnvironmentConfig().getHttp());
        final String PLATFORM_USERNAME = HttpServiceHelper.getCapPlatformUsername(configProvider.getEnvironmentConfig().getHttp());
        final String EXPECTED_EVENT_TYPE = PlayerAccountEventType.PLAYER_STATUS_UPDATE.getValue();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            UpdatePlayerPropertiesRequest blockRequest;
            UpdatePlayerPropertiesRequest unblockRequest;
            PlayerStatusUpdateMessage unblockStatusMessage;
            NatsMessage<NatsWalletBlockedPayload> walletUnblockedEvent;
            WalletProjectionMessage walletProjectionMessage;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(ctx.registeredPlayer.walletData(), "default_step.registration.wallet_data");
        });

        step("CAP API: Установка флага ручной блокировки игрока", () -> {
            ctx.blockRequest = UpdatePlayerPropertiesRequest.builder()
                    .manuallyBlocked(true)
                    .blockDeposit(false)
                    .blockWithdrawal(false)
                    .blockGambling(false)
                    .blockBetting(false)
                    .build();

            assertAll(
                    () -> assertNotNull(PLATFORM_NODE_ID, "config.platform.node_id"),
                    () -> assertNotNull(PLATFORM_USER_ID, "config.cap.platform_user_id"),
                    () -> assertNotNull(PLATFORM_USERNAME, "config.cap.platform_username")
            );

            var response = capAdminClient.updatePlayerProperties(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    PLATFORM_NODE_ID,
                    PLATFORM_USER_ID,
                    PLATFORM_USERNAME,
                    ctx.blockRequest
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_player_properties.block.status_code");
        });

        step("CAP API: Снятие флага ручной блокировки игрока", () -> {
            ctx.unblockRequest = UpdatePlayerPropertiesRequest.builder()
                    .manuallyBlocked(false)
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
                    ctx.unblockRequest
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_player_properties.unblock.status_code");
        });

        step("Kafka: Проверка события player.statusUpdate после снятия блокировки", () -> {
            ctx.unblockStatusMessage = kafkaClient.expect(PlayerStatusUpdateMessage.class)
                    .with("message.eventType", EXPECTED_EVENT_TYPE)
                    .with("player.externalId", ctx.registeredPlayer.walletData().playerUUID())
                    .with("player.status", PlayerAccountStatus.ACTIVE.getCode())
                    .unique()
                    .fetch();

            assertAll(
                    () -> assertEquals(PlayerAccountEventType.PLAYER_STATUS_UPDATE, ctx.unblockStatusMessage.message().eventType(),
                            "kafka.player_status_update.unblock.event_type"),
                    () -> assertNotNull(ctx.unblockStatusMessage.message().eventCreatedAt(),
                            "kafka.player_status_update.unblock.event_created_at"),
                    () -> assertNotNull(ctx.unblockStatusMessage.player(), "kafka.player_status_update.unblock.player"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(),
                            ctx.unblockStatusMessage.player().externalId(), "kafka.player_status_update.unblock.external_id"),
                    () -> assertTrue(ctx.unblockStatusMessage.player().activeStatus(),
                            "kafka.player_status_update.unblock.active_status"),
                    () -> assertEquals(PlayerAccountStatus.ACTIVE, ctx.unblockStatusMessage.player().status(),
                            "kafka.player_status_update.unblock.status")
            );
        });

        step("NATS: Проверка события wallet_blocked после снятия блокировки", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID()
            );

            ctx.walletUnblockedEvent = natsClient.expect(NatsWalletBlockedPayload.class)
                    .from(subject)
                    .withType(NatsEventType.WALLET_BLOCKED.getHeaderValue())
                    .unique()
                    .fetch();

            assertAll(
                    () -> assertNotNull(ctx.walletUnblockedEvent, "nats.wallet_blocked.unblock.event_not_null"),
                    () -> assertTrue(ctx.walletUnblockedEvent.getSequence() > 0,
                            "nats.wallet_blocked.unblock.sequence_positive"),
                    () -> assertTrue(ctx.walletUnblockedEvent.getPayload().date() >= 0,
                            "nats.wallet_blocked.unblock.date.non_negative")
            );
        });

        step("Kafka: Проверка события wallet_blocked после снятия блокировки", () -> {
            ctx.walletProjectionMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("type", ctx.walletUnblockedEvent.getType())
                    .with("seq_number", ctx.walletUnblockedEvent.getSequence())
                    .unique()
                    .fetch();

            var kafkaMessage = ctx.walletProjectionMessage;
            var expectedPayload = assertDoesNotThrow(() ->
                    objectMapper.writeValueAsString(ctx.walletUnblockedEvent.getPayload()));
            var expectedTimestamp = ctx.walletUnblockedEvent.getTimestamp().toEpochSecond();

            assertAll(
                    () -> assertEquals(ctx.walletUnblockedEvent.getType(), kafkaMessage.type(),
                            "kafka.wallet_blocked.unblock.type"),
                    () -> assertEquals(ctx.walletUnblockedEvent.getSequence(), kafkaMessage.seqNumber(),
                            "kafka.wallet_blocked.unblock.seq_number"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().walletUUID(), kafkaMessage.walletUuid(),
                            "kafka.wallet_blocked.unblock.wallet_uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), kafkaMessage.playerUuid(),
                            "kafka.wallet_blocked.unblock.player_uuid"),
                    () -> assertEquals(PLATFORM_NODE_ID, kafkaMessage.nodeUuid(),
                            "kafka.wallet_blocked.unblock.node_uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), kafkaMessage.currency(),
                            "kafka.wallet_blocked.unblock.currency"),
                    () -> assertEquals(expectedPayload, kafkaMessage.payload(),
                            "kafka.wallet_blocked.unblock.payload"),
                    () -> assertEquals(expectedTimestamp, kafkaMessage.timestamp(),
                            "kafka.wallet_blocked.unblock.timestamp"),
                    () -> assertNotNull(kafkaMessage.seqNumberNodeUuid(),
                            "kafka.wallet_blocked.unblock.seq_number_node_uuid")
            );
        });

        step("Redis (Wallet): Проверка статуса кошелька после снятия блокировки", () -> {
            assertNotNull(ctx.walletUnblockedEvent, "context.wallet_unblocked_event");

            var walletUuid = ctx.registeredPlayer.walletData().walletUUID();
            var walletAggregate = redisWalletClient
                    .key(walletUuid)
                    .withAtLeast("LastSeqNumber", ctx.walletUnblockedEvent.getSequence())
                    .fetch();

            assertAll(
                    () -> assertFalse(walletAggregate.isBlocked(), "redis.wallet.unblock.is_blocked"),
                    () -> assertTrue(walletAggregate.blockDate() >= 0, "redis.wallet.unblock.block_date")
            );
        });
    }
}
