package com.uplatform.wallet_tests.tests.wallet.admin;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
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

@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("PlayerBlockers")
@Suite("Позитивные сценарии: PlayerProperties")
@Tag("Wallet")
@Tag("CAP")
class PlayerManualBlockTest extends BaseTest {

    @Test
    @DisplayName("CAP: Блокировка игрока обновляет статус и отправляет события")
    void shouldBlockPlayerAndPublishEvents() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String platformUserId = HttpServiceHelper.getCapPlatformUserId(configProvider.getEnvironmentConfig().getHttp());
        final String platformUsername = HttpServiceHelper.getCapPlatformUsername(configProvider.getEnvironmentConfig().getHttp());
        final String expectedEventType = PlayerAccountEventType.PLAYER_STATUS_UPDATE.getValue();
        final PlayerAccountStatus expectedPlayerStatus = PlayerAccountStatus.BLOCKED;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            UpdatePlayerPropertiesRequest updateRequest;
            PlayerStatusUpdateMessage statusUpdateMessage;
            NatsMessage<NatsWalletBlockedPayload> walletBlockedEvent;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
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

            assertAll(
                    () -> assertNotNull(platformNodeId, "config.platform.node_id"),
                    () -> assertNotNull(platformUserId, "config.cap.platform_user_id"),
                    () -> assertNotNull(platformUsername, "config.cap.platform_username")
            );

            var response = capAdminClient.updatePlayerProperties(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    platformUserId,
                    platformUsername,
                    ctx.updateRequest
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_player_properties.status_code");
        });

        step("Kafka: Проверка события player.statusUpdate", () -> {
            ctx.statusUpdateMessage = kafkaClient.expect(PlayerStatusUpdateMessage.class)
                    .with("message.eventType", expectedEventType)
                    .with("player.externalId", ctx.registeredPlayer.walletData().playerUUID())
                    .with("player.status", expectedPlayerStatus.getCode())
                    .fetch();

            assertAll(
                    () -> assertNotNull(ctx.statusUpdateMessage.message(), "kafka.player_status_update.message"),
                    () -> assertEquals(PlayerAccountEventType.PLAYER_STATUS_UPDATE, ctx.statusUpdateMessage.message().eventType(),
                            "kafka.player_status_update.event_type"),
                    () -> assertNotNull(ctx.statusUpdateMessage.message().eventCreatedAt(),
                            "kafka.player_status_update.event_created_at"),
                    () -> assertNotNull(ctx.statusUpdateMessage.player(), "kafka.player_status_update.player"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(),
                            ctx.statusUpdateMessage.player().externalId(), "kafka.player_status_update.external_id"),
                    () -> assertFalse(ctx.statusUpdateMessage.player().activeStatus(),
                            "kafka.player_status_update.active_status"),
                    () -> assertEquals(expectedPlayerStatus, ctx.statusUpdateMessage.player().status(),
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
                    .fetch();

            assertAll(
                    () -> assertNotNull(ctx.walletBlockedEvent, "nats.wallet_blocked.event"),
                    () -> assertEquals(NatsEventType.WALLET_BLOCKED.getHeaderValue(), ctx.walletBlockedEvent.getType(),
                            "nats.wallet_blocked.type"),
                    () -> assertNotNull(ctx.walletBlockedEvent.getPayload(), "nats.wallet_blocked.payload"),
                    () -> assertNotNull(ctx.walletBlockedEvent.getPayload().date(), "nats.wallet_blocked.date"),
                    () -> assertTrue(ctx.walletBlockedEvent.getPayload().date() >= 0,
                            "nats.wallet_blocked.date.non_negative"),
                    () -> assertEquals(subject, ctx.walletBlockedEvent.getSubject(), "nats.wallet_blocked.subject")
            );
        });
    }
}
