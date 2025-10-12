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
 * Интеграционный тест, проверяющий позитивный сценарий снятия ручной блокировки с игрока через CAP API:
 * {@code PATCH /_cap/player/api/v1/admin/players/{playerId}/properties}.
 *
 * <p><b>Идея теста:</b> Обеспечить полную надежность и консистентность данных при выполнении обратной операции — снятия
 * ручной блокировки. Этот тест является логическим завершением сценария блокировки и гарантирует, что система
 * не только корректно блокирует аккаунт, но и безошибочно восстанавливает его статус. Проверяется, что команда
 * на разблокировку, инициированная через CAP API, запускает правильную цепочку событий, синхронизируя
 * состояние игрока во всех компонентах системы.</p>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <p>Тест эмулирует полный жизненный цикл: администратор сначала вручную блокирует игрока, а затем снимает эту блокировку.
 * Основное внимание уделяется проверке корректности процесса разблокировки и его последствий для всей системы.</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока.</li>
 *   <li><b>Предварительное условие:</b> Отправка запроса в CAP API для установки флага ручной блокировки {@code manuallyBlocked = true}.</li>
 *   <li><b>Основное действие:</b> Отправка второго запроса в CAP API для снятия флага ручной блокировки {@code manuallyBlocked = false}.</li>
 *   <li>Прослушивание и валидация Kafka-сообщения в топике {@code player.v1.account}, подтверждающего смену статуса
 *       игрока на {@link com.uplatform.wallet_tests.api.kafka.dto.player_status.enums.PlayerAccountStatus#INACTIVE}.</li>
 *   <li>Прослушивание и валидация нового NATS-события типа {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType#WALLET_BLOCKED},
 *       сигнализирующего об изменении статуса блокировки кошелька.</li>
 *   <li>Проверка того, что NATS-событие было успешно спроецировано в Kafka-топик {@code wallet.v8.projectionSource}.</li>
 *   <li>Проверка конечного состояния в Redis, чтобы убедиться, что кошелек больше не находится в заблокированном состоянии.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Оба запроса (на блокировку и на снятие блокировки) через CAP API выполняются успешно (HTTP 204 NO CONTENT).</li>
 *   <li>После снятия блокировки в Kafka топике {@code player.v1.account} появляется событие о смене статуса игрока на {@code INACTIVE}.</li>
 *   <li>В NATS публикуется новое событие типа {@code wallet_blocked}, отражающее актуальное состояние блокировки кошелька.</li>
 *   <li>Данные из NATS-события корректно дублируются в проекционный топик Kafka.</li>
 *   <li>Состояние кошелька в Redis обновляется, и флаг {@code isBlocked} устанавливается в {@code false}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Управление игроком")
@Suite("Ручная блокировка игрока: Позитивные сценарии")
@Tag("Wallet") @Tag("CAP")
class PlayerManualUnblockTest extends BaseTest {

    @Test
    @DisplayName("CAP: Ручная блокировка игрока - Отмена.")
    void shouldUnblockPlayerAndPublishEvents() {
        final String PLATFORM_NODE_ID = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String PLATFORM_USER_ID = HttpServiceHelper.getCapPlatformUserId(configProvider.getEnvironmentConfig().getHttp());
        final String PLATFORM_USERNAME = HttpServiceHelper.getCapPlatformUsername(configProvider.getEnvironmentConfig().getHttp());

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

        step("Kafka: Проверка события player.statusUpdate в топике player.v1.account", () -> {
            ctx.unblockStatusMessage = kafkaClient.expect(PlayerStatusUpdateMessage.class)
                    .with("message.eventType", PlayerAccountEventType.PLAYER_STATUS_UPDATE.getValue())
                    .with("player.externalId", ctx.registeredPlayer.walletData().playerUUID())
                    .with("player.status", PlayerAccountStatus.INACTIVE.getCode())
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
                    () -> assertFalse(ctx.unblockStatusMessage.player().activeStatus(),
                            "kafka.player_status_update.unblock.active_status"),
                    () -> assertEquals(PlayerAccountStatus.INACTIVE, ctx.unblockStatusMessage.player().status(),
                            "kafka.player_status_update.unblock.status")
            );
        });

        step("NATS: Проверка события wallet_blocked", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID()
            );

            ctx.walletUnblockedEvent = natsClient.expect(NatsWalletBlockedPayload.class)
                    .from(subject)
                    .withType(NatsEventType.WALLET_BLOCKED.getHeaderValue())
                    .unique()
                    .fetch();

            assertTrue(ctx.walletUnblockedEvent.getPayload().date() >= 0, "nats.wallet_blocked.unblock.date.non_negative");
        });

        step("Kafka: Проверка события wallet_blocked в топике wallet.v8.projectionSource", () -> {
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

            assertFalse(walletAggregate.isBlocked(), "redis.wallet.unblock.is_blocked");
        });
    }
}