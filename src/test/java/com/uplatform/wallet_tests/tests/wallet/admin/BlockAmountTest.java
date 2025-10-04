package com.uplatform.wallet_tests.tests.wallet.admin;

import com.uplatform.wallet_tests.tests.base.BaseTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountResponse;
import com.uplatform.wallet_tests.api.nats.dto.NatsBlockAmountEventPayload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.NAME;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("BlockAmount")
@Suite("Позитивные сценарии: BlockAmount")
@Tag("Wallet") @Tag("CAP")
class BlockAmountTest extends BaseTest {

    @Test
    @DisplayName("Проверка создания блокировки средств на кошельке игрока")
    void shouldCreateBlockAmountAndVerifyResponse() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal blockAmount = new BigDecimal("50.00");

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            CreateBlockAmountRequest blockAmountRequest;
            ResponseEntity<CreateBlockAmountResponse> blockAmountResponse;
            NatsMessage<NatsBlockAmountEventPayload> blockAmountEvent;
        }
        final TestContext ctx = new TestContext();
        

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);

            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("CAP API: Создание блокировки средств", () -> {
            ctx.blockAmountRequest = CreateBlockAmountRequest.builder()
                    .reason(get(NAME))
                    .currency(ctx.registeredPlayer.walletData().currency())
                    .amount(blockAmount.toString())
                    .build();

            ctx.blockAmountResponse = capAdminClient.createBlockAmount(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    ctx.blockAmountRequest
            );

            var responseBody = ctx.blockAmountResponse.getBody();
            var player = ctx.registeredPlayer.walletData();
            assertAll("Проверка данных в ответе на создание блокировки средств",
                    () -> assertEquals(HttpStatus.OK, ctx.blockAmountResponse.getStatusCode(), "cap_api.block_amount.status_code"),
                    () -> assertNotNull(responseBody.transactionId(), "cap_api.block_amount.transaction_id"),
                    () -> assertEquals(player.currency(), responseBody.currency(), "cap_api.block_amount.currency"),
                    () -> assertEquals(0, blockAmount.compareTo(responseBody.amount()), "cap_api.block_amount.amount"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), responseBody.reason(), "cap_api.block_amount.reason"),
                    () -> assertNotNull(responseBody.userId(), "cap_api.block_amount.user_id"),
                    () -> assertNotNull(responseBody.userName(), "cap_api.block_amount.user_name"),
                    () -> assertTrue(responseBody.createdAt() > 0, "cap_api.block_amount.created_at")
            );
        });

        step("NATS: Проверка поступления события block_amount_started", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.blockAmountEvent = natsClient.expect(NatsBlockAmountEventPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BLOCK_AMOUNT_STARTED.getHeaderValue())
                    .fetch();

            var actualPayload = ctx.blockAmountEvent.getPayload();
            var blockAmountResponse = ctx.blockAmountResponse.getBody();
            assertAll("Проверка основных полей NATS payload",
                    () -> assertEquals(blockAmountResponse.transactionId(), actualPayload.uuid(), "nats.payload.uuid"),
                    () -> assertEquals(NatsBlockAmountStatus.CREATED, actualPayload.status(), "nats.payload.status"),
                    () -> assertEquals(0, blockAmount.negate().compareTo(actualPayload.amount()), "nats.payload.amount"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), actualPayload.reason(), "nats.payload.reason"),
                    () -> assertEquals(NatsBlockAmountType.MANUAL , actualPayload.type(), "nats.payload.type"),
                    () -> assertEquals(blockAmountResponse.userId(), actualPayload.userUuid(), "nats.payload.user_uuid"),
                    () -> assertEquals(blockAmountResponse.userName(), actualPayload.userName(), "nats.payload.user_name"),
                    () -> assertEquals(blockAmountResponse.createdAt(), actualPayload.createdAt(), "nats.payload.created_at"),
                    () -> assertNotNull(actualPayload.expiredAt(), "nats.payload.expired_at")
            );
        });
        
        step("Kafka: Проверка поступления сообщения block_amount_started в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", ctx.blockAmountEvent.getSequence())
                    .fetch();
        
            assertTrue(utils.areEquivalent(kafkaMessage, ctx.blockAmountEvent), "kafka.payload");
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) ctx.blockAmountEvent.getSequence())
                    .fetch();

            var blockedAmountInfo = aggregate.blockedAmounts().get(0);
            var responseBody = ctx.blockAmountResponse.getBody();
            var expectedBalance = adjustmentAmount.subtract(blockAmount);

            assertAll("Проверка агрегата после BlockAmount",
                    () -> assertEquals((int) ctx.blockAmountEvent.getSequence(), aggregate.lastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, expectedBalance.compareTo(aggregate.balance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, expectedBalance.compareTo(aggregate.availableWithdrawalBalance()), "redis.aggregate.available_withdrawal_balance"),
                    () -> assertEquals(0, adjustmentAmount.compareTo(aggregate.balanceBefore()), "redis.aggregate.balance_before"),
                    () -> assertEquals(1, aggregate.blockedAmounts().size(), "redis.aggregate.blocked_amount.size"),
                    () -> assertEquals(responseBody.transactionId(), blockedAmountInfo.uuid(), "redis.aggregate.blocked_amount.uuid"),
                    () -> assertEquals(responseBody.userId(), blockedAmountInfo.userUUID(), "redis.aggregate.blocked_amount.user_uuid"),
                    () -> assertEquals(responseBody.userName(), blockedAmountInfo.userName(), "redis.aggregate.blocked_amount.user_name"),
                    () -> assertEquals(0, blockAmount.negate().compareTo(blockedAmountInfo.amount()), "redis.aggregate.blocked_amount.amount"),
                    () -> assertEquals(0, blockAmount.compareTo(blockedAmountInfo.deltaAvailableWithdrawalBalance()), "redis.aggregate.blocked_amount.delta_available_withdrawal_balance"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), blockedAmountInfo.reason(), "redis.aggregate.blocked_amount.reason"),
                    () -> assertEquals(NatsBlockAmountType.MANUAL.getValue(), blockedAmountInfo.type(), "redis.aggregate.blocked_amount.type"),
                    () -> assertEquals(NatsBlockAmountStatus.CREATED.getValue(), blockedAmountInfo.status(), "redis.aggregate.blocked_amount.status"),
                    () -> assertNotNull(blockedAmountInfo.createdAt(), "redis.aggregate.blocked_amount.created_at"),
                    () -> assertNotNull(blockedAmountInfo.expiredAt(), "redis.aggregate.blocked_amount.expired_at")
            );
        });

        step("CAP API: Получение списка блокировок", () -> {
            var response = capAdminClient.getBlockAmountList(
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    ctx.registeredPlayer.walletData().playerUUID());

            var expectedTxId = ctx.blockAmountResponse.getBody().transactionId();
            var createdItem = response.getBody().items().get(0);
            var player = ctx.registeredPlayer.walletData();

            assertAll("Проверка данных созданной блокировки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.status_code"),
                    () -> assertEquals(expectedTxId, createdItem.transactionId(), "cap_api.block_amount_list.transaction_id"),
                    () -> assertEquals(ctx.blockAmountRequest.getCurrency(), createdItem.currency(), "cap_api.block_amount_list.currency"),
                    () -> assertEquals(0, blockAmount.negate().compareTo(createdItem.amount()), "cap_api.block_amount_list.amount"),
                    () -> assertEquals(ctx.blockAmountRequest.getReason(), createdItem.reason(), "cap_api.block_amount_list.reason"),
                    () -> assertNotNull(createdItem.userId(), "cap_api.block_amount_list.user_id"),
                    () -> assertNotNull(createdItem.userName(), "cap_api.block_amount_list.user_name"),
                    () -> assertNotNull(createdItem.createdAt(), "cap_api.block_amount_list.created_at_is_null"),
                    () -> assertEquals(player.walletUUID(), createdItem.walletId(), "cap_api.block_amount_list.wallet_id"),
                    () -> assertEquals(player.playerUUID(), createdItem.playerId(), "cap_api.block_amount_list.player_id")
            );
        });
    }
}
