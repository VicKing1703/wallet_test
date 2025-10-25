package com.uplatform.wallet_tests.tests.wallet.admin;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountResponse;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsBlockAmountEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountType;
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
 * Интеграционный тест, проверяющий позитивный сценарий ручной блокировки части баланса игрока через CAP API:
 * {@code POST /_cap/api/v1/wallet/{playerUuid}/create-block-amount}.
 *
 * <p><b>Идея теста:</b> Гарантировать полную сквозную надежность процесса ручной блокировки средств, которая является
 * критически важной операцией для административного контроля и управления финансами игроков (например, при расследовании
 * инцидентов). Тест подтверждает, что запрос на блокировку, инициированный через CAP API, корректно запускает всю
 * цепочку событий: от создания транзакции и генерации NATS-сообщения до финального обновления состояния кошелька в Redis.
 * Это обеспечивает целостность данных и гарантирует, что заблокированные средства действительно становятся недоступными
 * для игрока во всей системе.</p>
 *
 * <p><b>Сценарий тестирования:</b></p>
 * <p>Тест покрывает полный жизненный цикл создания одной ручной блокировки: от API-запроса на создание до проверки
 * ее наличия в списке активных блокировок, подтверждая консистентность данных на каждом этапе.</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока и пополнение его баланса на начальную сумму.</li>
 *   <li>Отправка запроса в CAP API для создания ручной блокировки на часть баланса.</li>
 *   <li>Проверка успешного ответа от CAP API с данными о созданной блокировке.</li>
 *   <li>Прослушивание и валидация NATS-события типа {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType#BLOCK_AMOUNT_STARTED},
 *       подтверждающего начало процесса блокировки средств.</li>
 *   <li>Проверка того, что NATS-событие было успешно спроецировано в Kafka-топик {@code wallet.v8.projectionSource}.</li>
 *   <li>Проверка конечного состояния в Redis, чтобы убедиться, что баланс кошелька уменьшился на сумму блокировки,
 *       и в агрегате появилась запись о новой блокировке.</li>
 *   <li>Отправка запроса в CAP API для получения списка активных блокировок игрока и проверка наличия
 *       в нем только что созданной блокировки.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос на создание блокировки через CAP API выполняется успешно (HTTP 200 OK) и возвращает корректные данные.</li>
 *   <li>В NATS публикуется событие {@code block_amount_started} с корректным payload.</li>
 *   <li>Данные из NATS-события полностью дублируются в проекционный топик Kafka {@code wallet.v8.projectionSource}.</li>
 *   <li>Состояние кошелька в Redis обновляется: {@code balance} уменьшается, а в списке {@code blockedAmounts} появляется новая запись.</li>
 *   <li>Запрос на получение списка блокировок через CAP API возвращает созданную блокировку с верными параметрами.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Управление игроком")
@Suite("Ручная блокировка баланса игрока: Позитивные сценарии")
@Tag("Wallet") @Tag("CAP")
class CreateBlockAmountTest extends BaseTest {

    @Test
    @DisplayName("CAP: Ручная блокировка баланса игрока - Создание.")
    void shouldCreateBlockAmountAndVerifyEvents() {
        final String PLATFORM_NODE_ID = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String PLATFORM_USER_ID = HttpServiceHelper.getCapPlatformUserId(configProvider.getEnvironmentConfig().getHttp());
        final String PLATFORM_USERNAME = HttpServiceHelper.getCapPlatformUsername(configProvider.getEnvironmentConfig().getHttp());
        final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("150.00");
        final BigDecimal BLOCK_AMOUNT = new BigDecimal("50.00");

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            CreateBlockAmountRequest blockAmountRequest;
            CreateBlockAmountResponse blockAmountResponse;
            NatsMessage<NatsBlockAmountEventPayload> blockAmountEvent;
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

            var player = ctx.registeredPlayer.walletData();
            assertAll("Проверка данных в ответе на создание блокировки средств",
                    () -> assertNotNull(ctx.blockAmountResponse.transactionId(), "cap_api.create_block_amount.transaction_id"),
                    () -> assertEquals(player.currency(), ctx.blockAmountResponse.currency(), "cap_api.create_block_amount.currency"),
                    () -> assertEquals(0, BLOCK_AMOUNT.compareTo(ctx.blockAmountResponse.amount()), "cap_api.create_block_amount.amount"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), ctx.blockAmountResponse.reason(), "cap_api.create_block_amount.reason"),
                    () -> assertEquals(PLATFORM_USER_ID, ctx.blockAmountResponse.userId(), "cap_api.create_block_amount.user_id"),
                    () -> assertEquals(PLATFORM_USERNAME, ctx.blockAmountResponse.userName(), "cap_api.create_block_amount.user_name"),
                    () -> assertTrue(ctx.blockAmountResponse.createdAt() > 0, "cap_api.create_block_amount.created_at")
            );
        });

        step("NATS: Проверка события block_amount_started", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.blockAmountEvent = natsClient.expect(NatsBlockAmountEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BLOCK_AMOUNT_STARTED.getHeaderValue())
                    .unique()
                    .fetch();

            var actualPayload = ctx.blockAmountEvent.getPayload();
            var responseBody = ctx.blockAmountResponse;
            assertAll("Проверка полей NATS-события block_amount_started",
                    () -> assertEquals(responseBody.transactionId(), actualPayload.uuid(), "nats.block_amount_started.uuid"),
                    () -> assertEquals(NatsBlockAmountStatus.CREATED, actualPayload.status(), "nats.block_amount_started.status"),
                    () -> assertEquals(0, BLOCK_AMOUNT.negate().compareTo(actualPayload.amount()), "nats.block_amount_started.amount"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), actualPayload.reason(), "nats.block_amount_started.reason"),
                    () -> assertEquals(NatsBlockAmountType.MANUAL , actualPayload.type(), "nats.block_amount_started.type"),
                    () -> assertEquals(responseBody.userId(), actualPayload.userUuid(), "nats.block_amount_started.user_uuid"),
                    () -> assertEquals(responseBody.userName(), actualPayload.userName(), "nats.block_amount_started.user_name"),
                    () -> assertEquals(responseBody.createdAt(), actualPayload.createdAt(), "nats.block_amount_started.created_at"),
                    () -> assertNotNull(actualPayload.expiredAt(), "nats.block_amount_started.expired_at")
            );
        });

        step("Kafka: Проверка события block_amount_started в топике wallet.v8.projectionSource", () -> {
            ctx.walletProjectionMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("type", ctx.blockAmountEvent.getType())
                    .with("seq_number", ctx.blockAmountEvent.getSequence())
                    .unique()
                    .fetch();

            var kafkaMessage = ctx.walletProjectionMessage;
            var expectedTimestamp = ctx.blockAmountEvent.getTimestamp().toEpochSecond();
            var kafkaPayload = assertDoesNotThrow(() ->
                    objectMapper.readTree(kafkaMessage.payload()));
            var natsPayload = ctx.blockAmountEvent.getPayload();

            assertAll("Проверка полей Kafka-сообщения, спроецированного из NATS",
                    () -> assertEquals(ctx.blockAmountEvent.getType(), kafkaMessage.type(), "kafka.block_amount_started.type"),
                    () -> assertEquals(ctx.blockAmountEvent.getSequence(), kafkaMessage.seqNumber(), "kafka.block_amount_started.seq_number"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().walletUUID(), kafkaMessage.walletUuid(), "kafka.block_amount_started.wallet_uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().playerUUID(), kafkaMessage.playerUuid(), "kafka.block_amount_started.player_uuid"),
                    () -> assertEquals(PLATFORM_NODE_ID, kafkaMessage.nodeUuid(), "kafka.block_amount_started.node_uuid"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().currency(), kafkaMessage.currency(), "kafka.block_amount_started.currency"),
                    () -> assertEquals(natsPayload.uuid(), kafkaPayload.get("uuid").asText(), "kafka.block_amount_started.payload.uuid"),
                    () -> assertEquals(natsPayload.status().getValue(), kafkaPayload.get("status").asInt(), "kafka.block_amount_started.payload.status"),
                    () -> assertEquals(0, BLOCK_AMOUNT.negate().compareTo(new BigDecimal(kafkaPayload.get("amount").asText())), "kafka.block_amount_started.payload.amount"),
                    () -> assertEquals(natsPayload.reason(), kafkaPayload.get("reason").asText(), "kafka.block_amount_started.payload.reason"),
                    () -> assertEquals(natsPayload.type().getValue(), kafkaPayload.get("type").asInt(), "kafka.block_amount_started.payload.type"),
                    () -> assertEquals(natsPayload.userUuid(), kafkaPayload.get("user_uuid").asText(), "kafka.block_amount_started.payload.user_uuid"),
                    () -> assertEquals(natsPayload.userName(), kafkaPayload.get("user_name").asText(), "kafka.block_amount_started.payload.user_name"),
                    () -> assertEquals(natsPayload.createdAt(), kafkaPayload.get("created_at").asLong(), "kafka.block_amount_started.payload.created_at"),
                    () -> assertEquals(expectedTimestamp, kafkaMessage.timestamp(), "kafka.block_amount_started.timestamp"),
                    () -> assertNotNull(kafkaMessage.seqNumberNodeUuid(), "kafka.block_amount_started.seq_number_node_uuid")
            );
        });

        step("Redis (Wallet): Проверка состояния кошелька после блокировки", () -> {
            assertNotNull(ctx.blockAmountEvent, "context.block_amount_event");

            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", ctx.blockAmountEvent.getSequence())
                    .fetch();

            var blockedAmountInfo = aggregate.blockedAmounts().get(0);
            var responseBody = ctx.blockAmountResponse;
            var expectedBalance = ADJUSTMENT_AMOUNT.subtract(BLOCK_AMOUNT);

            assertAll("Проверка агрегата кошелька в Redis после блокировки средств",
                    () -> assertEquals(ctx.blockAmountEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, expectedBalance.compareTo(aggregate.balance()), "redis.wallet.balance"),
                    () -> assertEquals(0, expectedBalance.compareTo(aggregate.availableWithdrawalBalance()), "redis.wallet.available_withdrawal_balance"),
                    () -> assertEquals(0, ADJUSTMENT_AMOUNT.compareTo(aggregate.balanceBefore()), "redis.wallet.balance_before"),
                    () -> assertEquals(1, aggregate.blockedAmounts().size(), "redis.wallet.blocked_amounts.size"),
                    () -> assertEquals(responseBody.transactionId(), blockedAmountInfo.uuid(), "redis.wallet.blocked_amount.uuid"),
                    () -> assertEquals(responseBody.userId(), blockedAmountInfo.userUUID(), "redis.wallet.blocked_amount.user_uuid"),
                    () -> assertEquals(responseBody.userName(), blockedAmountInfo.userName(), "redis.wallet.blocked_amount.user_name"),
                    () -> assertEquals(0, BLOCK_AMOUNT.negate().compareTo(blockedAmountInfo.amount()), "redis.wallet.blocked_amount.amount"),
                    () -> assertEquals(0, BLOCK_AMOUNT.compareTo(blockedAmountInfo.deltaAvailableWithdrawalBalance()), "redis.wallet.blocked_amount.delta_available_withdrawal_balance"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), blockedAmountInfo.reason(), "redis.wallet.blocked_amount.reason"),
                    () -> assertEquals(NatsBlockAmountType.MANUAL.getValue(), blockedAmountInfo.type(), "redis.wallet.blocked_amount.type"),
                    () -> assertEquals(NatsBlockAmountStatus.CREATED.getValue(), blockedAmountInfo.status(), "redis.wallet.blocked_amount.status"),
                    () -> assertNotNull(blockedAmountInfo.createdAt(), "redis.wallet.blocked_amount.created_at"),
                    () -> assertNotNull(blockedAmountInfo.expiredAt(), "redis.wallet.blocked_amount.expired_at")
            );
        });

        step("CAP API: Проверка наличия созданной блокировки в списке блокировок игрока", () -> {
            var response = capAdminClient.getBlockAmountList(
                    utils.getAuthorizationHeader(),
                    PLATFORM_NODE_ID,
                    ctx.registeredPlayer.walletData().playerUUID());

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.get_block_amount_list.status_code");
            assertNotNull(response.getBody(), "cap_api.get_block_amount_list.response_body");
            assertEquals(1, response.getBody().items().size(), "cap_api.get_block_amount_list.items_size");

            var createdItem = response.getBody().items().get(0);
            var player = ctx.registeredPlayer.walletData();
            var expectedTxId = ctx.blockAmountResponse.transactionId();

            assertAll("Проверка данных созданной блокировки в списке",
                    () -> assertEquals(expectedTxId, createdItem.transactionId(), "cap_api.get_block_amount_list.transaction_id"),
                    () -> assertEquals(ctx.blockAmountRequest.getCurrency(), createdItem.currency(), "cap_api.get_block_amount_list.currency"),
                    () -> assertEquals(0, BLOCK_AMOUNT.negate().compareTo(createdItem.amount()), "cap_api.get_block_amount_list.amount"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), createdItem.reason(), "cap_api.get_block_amount_list.reason"),
                    () -> assertEquals(PLATFORM_USER_ID, createdItem.userId(), "cap_api.get_block_amount_list.user_id"),
                    () -> assertEquals(PLATFORM_USERNAME, createdItem.userName(), "cap_api.get_block_amount_list.user_name"),
                    () -> assertTrue(createdItem.createdAt() > 0, "cap_api.get_block_amount_list.created_at"),
                    () -> assertEquals(player.walletUUID(), createdItem.walletId(), "cap_api.get_block_amount_list.wallet_id"),
                    () -> assertEquals(player.playerUUID(), createdItem.playerId(), "cap_api.get_block_amount_list.player_id")
            );
        });
    }
}