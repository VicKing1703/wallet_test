package com.uplatform.wallet_tests.tests.wallet.admin;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountResponse;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.BlockAmountRevokedEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.NAME;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий процесс отмены ручной блокировки баланса игрока через CAP API.
 *
 * <p>Этот тест является логическим продолжением теста на создание блокировки и проверяет обратную операцию.
 * Сценарий эмулирует действия администратора, который сначала блокирует часть средств на счете игрока,
 * а затем отменяет эту блокировку. Тест подтверждает, что система корректно обрабатывает запрос на отмену,
 * генерирует событие о снятии блокировки, обновляет состояние кошелька и возвращает средства на основной баланс.</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока и пополнение его баланса.</li>
 *   <li>Создание ручной блокировки на часть баланса через CAP API в качестве предварительного условия.</li>
 *   <li><b>Основное действие:</b> Отправка запроса в CAP API для отмены (удаления) ранее созданной блокировки.</li>
 *   <li>Проверка успешного ответа от CAP API (HTTP 204 NO CONTENT).</li>
 *   <li>Прослушивание и валидация NATS-события типа {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType#BLOCK_AMOUNT_REVOKED},
 *       подтверждающего отмену блокировки.</li>
 *   <li>Проверка того, что NATS-событие было успешно спроецировано в Kafka-топик {@code wallet.v8.projectionSource}.</li>
 *   <li>Проверка конечного состояния в Redis, чтобы убедиться, что баланс кошелька восстановлен, а статус блокировки
 *       изменен на {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountStatus#REVOKED}.</li>
 *   <li>Проверка через CAP API, что отмененная блокировка больше не числится в списке активных.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос на отмену блокировки через CAP API выполняется успешно.</li>
 *   <li>В NATS публикуется событие {@code block_amount_revoked} с корректными данными.</li>
 *   <li>Данные из NATS-события полностью дублируются в проекционный топик Kafka.</li>
 *   <li>Состояние кошелька в Redis обновляется: баланс возвращается к исходному значению до блокировки,
 *       а у соответствующей блокировки в списке {@code blockedAmounts} меняется статус.</li>
 *   <li>Отмененная блокировка не отображается в списке активных блокировок игрока.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Управление игроком")
@Suite("Ручная блокировка баланса игрока: Позитивные сценарии")
@Tag("Wallet") @Tag("CAP")
class DeleteBlockAmountTest extends BaseTest {

    @Test
    @DisplayName("CAP: Ручная блокировка баланса игрока - Отмена.")
    void shouldDeleteBlockAmountAndVerifyEvents() {
        final String PLATFORM_NODE_ID = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String PLATFORM_USER_ID = HttpServiceHelper.getCapPlatformUserId(configProvider.getEnvironmentConfig().getHttp());
        final String PLATFORM_USERNAME = HttpServiceHelper.getCapPlatformUsername(configProvider.getEnvironmentConfig().getHttp());
        final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
        final BigDecimal BLOCK_AMOUNT = new BigDecimal("50.00");

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            CreateBlockAmountRequest blockAmountRequest;
            CreateBlockAmountResponse blockAmountResponse;
            NatsMessage<BlockAmountRevokedEventPayload> blockAmountRevokedEvent;
            WalletProjectionMessage walletProjectionMessage;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация и пополнение баланса нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(ADJUSTMENT_AMOUNT);
            assertNotNull(ctx.registeredPlayer.walletData(), "default_step.registration.wallet_data");
        });

        step("CAP API: Создание ручной блокировки средств", () -> {
            ctx.blockAmountRequest = CreateBlockAmountRequest.builder()
                    .reason(get(NAME))
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .amount(BLOCK_AMOUNT.toString())
                    .build();

            var response = capAdminClient.createBlockAmount(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    PLATFORM_NODE_ID,
                    ctx.blockAmountRequest
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.create_block_amount.status_code");
            ctx.blockAmountResponse = response.getBody();
            assertNotNull(ctx.blockAmountResponse, "cap_api.create_block_amount.response_body");
            assertNotNull(ctx.blockAmountResponse.transactionId(), "cap_api.create_block_amount.transaction_id");
        });

        step("CAP API: Отмена (удаление) блокировки средств", () -> {
            var response = capAdminClient.deleteBlockAmount(
                    ctx.blockAmountResponse.transactionId(),
                    utils.getAuthorizationHeader(),
                    PLATFORM_NODE_ID,
                    ctx.registeredPlayer.walletData().walletUUID(),
                    ctx.registeredPlayer.walletData().playerUUID()
            );
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.delete_block_amount.status_code");
        });

        step("NATS: Проверка события block_amount_revoked", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.blockAmountRevokedEvent = natsClient.expect(BlockAmountRevokedEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BLOCK_AMOUNT_REVOKED.getHeaderValue())
                    .unique()
                    .fetch();

            var payload = ctx.blockAmountRevokedEvent.getPayload();
            assertAll("Проверка полей в NATS-событии отмены блокировки",
                    () -> assertEquals(ctx.blockAmountResponse.transactionId(), payload.uuid(), "nats.block_amount_revoked.uuid"),
                    () -> assertEquals(PLATFORM_USER_ID, payload.userUuid(), "nats.block_amount_revoked.user_uuid"),
                    () -> assertEquals(PLATFORM_USERNAME, payload.userName(), "nats.block_amount_revoked.user_name"),
                    () -> assertEquals(PLATFORM_NODE_ID, payload.nodeUuid(), "nats.block_amount_revoked.node_uuid")
            );
        });

        step("Kafka: Проверка события block_amount_revoked в топике wallet.v8.projectionSource", () -> {
            ctx.walletProjectionMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("type", ctx.blockAmountRevokedEvent.getType())
                    .with("seq_number", ctx.blockAmountRevokedEvent.getSequence())
                    .unique()
                    .fetch();

            var kafkaMessage = ctx.walletProjectionMessage;
            var expectedTimestamp = ctx.blockAmountRevokedEvent.getTimestamp().toEpochSecond();
            var kafkaPayload = assertDoesNotThrow(() ->
                    objectMapper.readTree(kafkaMessage.payload()));

            assertAll("Проверка полей Kafka-сообщения, спроецированного из NATS",
                    () -> assertEquals(ctx.blockAmountRevokedEvent.getType(), kafkaMessage.type(), "kafka.block_amount_revoked.type"),
                    () -> assertEquals(ctx.blockAmountRevokedEvent.getSequence(), kafkaMessage.seqNumber(), "kafka.block_amount_revoked.seq_number"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().walletUUID(), kafkaMessage.walletUuid(), "kafka.block_amount_revoked.wallet_uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), kafkaMessage.playerUuid(), "kafka.block_amount_revoked.player_uuid"),
                    () -> assertEquals(ctx.blockAmountResponse.transactionId(), kafkaPayload.get("uuid").asText(), "kafka.block_amount_revoked.payload.uuid"),
                    () -> assertEquals(PLATFORM_NODE_ID, kafkaPayload.get("node_uuid").asText(), "kafka.block_amount_revoked.payload.node_uuid"),
                    () -> assertEquals(expectedTimestamp, kafkaMessage.timestamp(), "kafka.block_amount_revoked.timestamp")
            );
        });

        step("Redis (Wallet): Проверка состояния кошелька после отмены блокировки", () -> {
            assertNotNull(ctx.blockAmountRevokedEvent, "context.block_amount_revoked_event");

            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", ctx.blockAmountRevokedEvent.getSequence())
                    .fetch();

            var revokedBlockAmountId = ctx.blockAmountResponse.transactionId();
            var blockedAmount = aggregate.blockedAmounts().stream()
                    .filter(block -> block.uuid().equals(revokedBlockAmountId))
                    .findFirst();

            assertAll("Проверка агрегата кошелька в Redis после отмены блокировки",
                    () -> assertEquals(ctx.blockAmountRevokedEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ADJUSTMENT_AMOUNT.compareTo(aggregate.balance()), "redis.wallet.balance"),
                    () -> assertEquals(0, ADJUSTMENT_AMOUNT.compareTo(aggregate.availableWithdrawalBalance()), "redis.wallet.available_withdrawal_balance"),
                    () -> assertEquals(0, ADJUSTMENT_AMOUNT.subtract(BLOCK_AMOUNT).compareTo(aggregate.balanceBefore()), "redis.wallet.balance_before"),
                    () -> assertTrue(blockedAmount.isPresent(), "redis.wallet.blocked_amount.exists"),
                    () -> assertEquals(NatsBlockAmountStatus.REVOKED.getValue(), blockedAmount.get().status(), "redis.wallet.blocked_amount.status"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), blockedAmount.get().reason(), "redis.wallet.blocked_amount.reason")
            );
        });

        step("CAP API: Проверка отсутствия отмененной блокировки в списке активных", () -> {
            var response = capAdminClient.getBlockAmountList(
                    utils.getAuthorizationHeader(),
                    PLATFORM_NODE_ID,
                    ctx.registeredPlayer.walletData().playerUUID());

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.get_block_amount_list.status_code");
            assertNotNull(response.getBody(), "cap_api.get_block_amount_list.response_body");
            assertTrue(response.getBody().items().isEmpty(), "cap_api.get_block_amount_list.is_empty");
        });
    }
}