package com.uplatform.wallet_tests.tests.wallet.limit.deposit;

import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.deposit.SetDepositLimitRequest;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.testing.multisource.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий процесс создания лимита на депозит с различными
 * периодами действия (ежедневный, еженедельный, ежемесячный) и его корректное
 * отражение в различных системах.
 *
 * <p>Каждая итерация параметризованного теста выполняется с полностью изолированным
 * состоянием, включая создание нового игрока.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> установка лимита Deposit через Public API.</li>
 *   <li><b>Проверка Kafka:</b> сообщение limits.v2.</li>
 *   <li><b>Проверка NATS:</b> событие limit_changed_v2.</li>
 *   <li><b>Проверка Kafka Projection:</b> сопоставление с NATS.</li>
 *   <li><b>Проверка Redis:</b> агрегат кошелька содержит лимит.</li>
 *   <li><b>Проверка CAP:</b> получение лимитов игрока.</li>
 *   <li><b>Проверка Public API:</b> получение лимита Deposit.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: установка и получение лимита Deposit.</li>
 *   <li>Kafka: limits.v2.</li>
 *   <li>NATS: limit_changed_v2.</li>
 *   <li>Redis: агрегат кошелька.</li>
 *   <li>CAP API: список лимитов игрока.</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Limits")
@Feature("DepositLimit")
@Suite("Позитивные сценарии: DepositLimit")
@Tag("Limits") @Tag("Wallet") @Tag("Payment")
public class DepositLimitCreateTest extends BaseParameterizedTest {

    private static final BigDecimal limitAmount = new BigDecimal("100.15");

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Создание и валидация DepositLimit")
    void createDepositLimit(NatsLimitIntervalType periodType) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            SetDepositLimitRequest setLimitRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> natsLimitChangeEvent;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);

            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на депозит", () -> {
            testData.setLimitRequest = SetDepositLimitRequest.builder()
                    .currency(testData.registeredPlayer.getWalletData().currency())
                    .type(periodType)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setDepositLimit(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    testData.setLimitRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_deposit_limit.status_code");
        });

        step("Kafka: Проверка получения события limits.v2", () -> {
            var expectedAmount = limitAmount.stripTrailingZeros().toPlainString();

            testData.kafkaLimitMessage = kafkaClient.expect(LimitMessage.class)
                    .with("playerId", testData.registeredPlayer.getWalletData().playerUUID())
                    .with("limitType", NatsLimitType.DEPOSIT.getValue())
                    .with("currencyCode", testData.registeredPlayer.getWalletData().currency())
                    .with("amount", expectedAmount
            )
                    .fetch();

            assertNotNull(testData.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");

            assertAll("kafka.limits_v2_event.content_validation",
                    () -> assertEquals(NatsLimitType.DEPOSIT.getValue(), testData.kafkaLimitMessage.limitType(), "kafka.limits_v2_event.limitType"),
                    () -> assertEquals(periodType.getValue(), testData.kafkaLimitMessage.intervalType(), "kafka.limits_v2_event.intervalType"),
                    () -> assertEquals(0, limitAmount.compareTo(new BigDecimal(testData.kafkaLimitMessage.amount())), "kafka.limits_v2_event.amount"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().currency(), testData.kafkaLimitMessage.currencyCode(), "kafka.limits_v2_event.currencyCode")
            );
        });

        step("NATS: Проверка получения события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().playerUUID(),
                    testData.registeredPlayer.getWalletData().walletUUID());

            testData.natsLimitChangeEvent = natsClient.expect(NatsLimitChangedV2Payload.class)
                    .from(subject)
                    .withType(NatsEventType.LIMIT_CHANGED_V2.getHeaderValue())
                    .with("$.limits[0].external_id", testData.kafkaLimitMessage.id())
                    .fetch();

            assertNotNull(testData.natsLimitChangeEvent, "nats.limit_changed_v2_event.message_not_null");
            assertNotNull(testData.natsLimitChangeEvent.getPayload(), "nats.limit_changed_v2_event.payload_not_null");

            var natsLimit = testData.natsLimitChangeEvent.getPayload().getLimits().get(0);
            assertAll("nats.limit_changed_v2_event.content_validation",
                    () -> assertEquals(NatsLimitEventType.CREATED.getValue(), testData.natsLimitChangeEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(testData.kafkaLimitMessage.id(), natsLimit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.DEPOSIT.getValue(), natsLimit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), natsLimit.getIntervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                    () -> assertEquals(0, limitAmount.compareTo(natsLimit.amount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(testData.kafkaLimitMessage.currencyCode(), natsLimit.getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertEquals(testData.kafkaLimitMessage.expiresAt(), natsLimit.expiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(natsLimit.getStatus(), "nats.limit_changed_v2_event.limit.status_is_true")
            );
        });

        step("Kafka Projection: Сравнение данных из NATS и Kafka Wallet Projection", () -> {
            var projectionMsg = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("seq_number", testData.natsLimitChangeEvent.getSequence())
                    .fetch();

            assertNotNull(projectionMsg, "kafka.wallet_projection.message_not_null");
            assertTrue(utils.areEquivalent(projectionMsg, testData.natsLimitChangeEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis (Wallet Aggregate): Проверка данных лимита в агрегате кошелька", () -> {
            var aggregate = redisWalletClient
                    .key(testData.registeredPlayer.getWalletData().walletUUID())
                    .withAtLeast("LastSeqNumber", (int) testData.natsLimitChangeEvent.getSequence())
                    .fetch();

            assertFalse(aggregate.limits().isEmpty(), "redis.wallet_aggregate.limits_list_not_empty");

            var redisLimitOpt = aggregate.limits().stream()
                    .filter(l -> testData.kafkaLimitMessage.id().equals(l.getExternalID()) &&
                            NatsLimitType.DEPOSIT.getValue().equals(l.getLimitType()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.limit_found");
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(testData.kafkaLimitMessage.id(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.DEPOSIT.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), redisLimit.getIntervalType(), "redis.wallet_aggregate.limit.intervalType"),
                    () -> assertEquals(0, limitAmount.compareTo(redisLimit.amount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(redisLimit.spent()), "redis.wallet_aggregate.limit.spent_is_zero"),
                    () -> assertEquals(0, limitAmount.compareTo(redisLimit.rest()), "redis.wallet_aggregate.limit.rest_equals_amount"),
                    () -> assertEquals(testData.kafkaLimitMessage.currencyCode(), redisLimit.getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(redisLimit.startedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertEquals(testData.kafkaLimitMessage.expiresAt(), redisLimit.expiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(redisLimit.status(), "redis.wallet_aggregate.limit.status_is_true")
            );
        });

        step("CAP: Получение и валидация лимитов игрока", () -> {
            var response = capAdminClient.getPlayerLimits(
                    testData.registeredPlayer.getWalletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code");
            assertNotNull(response.getBody(), "cap.get_player_limits.response_body_not_null");
            assertFalse(response.getBody().data().isEmpty(), "cap.get_player_limits.response_body.data_list_not_empty");

            var capLimitOpt = response.getBody().data().stream()
                    .filter(l -> limitAmount.compareTo(l.amount()) == 0)
                    .findFirst();

            assertTrue(capLimitOpt.isPresent(), "cap.get_player_limits.limit_found");
            var capLimit = capLimitOpt.get();

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertTrue(capLimit.status(), "cap.get_player_limits.limit.status_is_true"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().currency(), capLimit.currency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, limitAmount.compareTo(capLimit.amount()), "cap.get_player_limits.limit.amount"),
                    () -> assertEquals(0, limitAmount.compareTo(capLimit.rest()), "cap.get_player_limits.limit.rest_equals_amount"),
                    () -> assertNotNull(capLimit.createdAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(capLimit.deactivatedAt(), "cap.get_player_limits.limit.deactivatedAt_is_null_for_active"),
                    () -> assertNotNull(capLimit.startedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNotNull(capLimit.expiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение и валидация лимита Deposit", () -> {
            var response = publicClient.getDepositLimits(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_deposit_limits.status_code");
            assertNotNull(response.getBody(), "fapi.get_deposit_limits.response_body_not_null");
            assertFalse(response.getBody().isEmpty(), "fapi.get_deposit_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> testData.kafkaLimitMessage.id().equals(l.id()))
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_deposit_limits.deposit_limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_deposit_limits.limit_content_validation",
                    () -> assertEquals(testData.kafkaLimitMessage.id(), fapiLimit.id(), "fapi.get_deposit_limits.limit.id"),
                    () -> assertEquals(periodType.getValue(), fapiLimit.type(), "fapi.get_deposit_limits.limit.type"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().currency(), fapiLimit.currency(), "fapi.get_deposit_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.status(), "fapi.get_deposit_limits.limit.status_is_true"),
                    () -> assertEquals(0, limitAmount.compareTo(fapiLimit.amount()), "fapi.get_deposit_limits.limit.amount"),
                    () -> assertEquals(0, limitAmount.compareTo(fapiLimit.rest()), "fapi.get_deposit_limits.limit.rest_equals_amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(fapiLimit.spent()), "fapi.get_deposit_limits.limit.spent_is_zero"),
                    () -> assertNotNull(fapiLimit.startedAt(), "fapi.get_deposit_limits.limit.startedAt"),
                    () -> assertNotNull(fapiLimit.expiresAt(), "fapi.get_deposit_limits.limit.expiresAt"),
                    () -> assertNull(fapiLimit.deactivatedAt(), "fapi.get_deposit_limits.limit.deactivatedAt_is_null"),
                    () -> assertFalse(fapiLimit.required(), "fapi.get_deposit_limits.limit.isRequired_flag"),
                    () -> {
                        assertNotNull(fapiLimit.upcomingChanges(), "fapi.get_deposit_limits.limit.upcomingChanges_not_null");
                        assertTrue(fapiLimit.upcomingChanges().isEmpty(), "fapi.get_deposit_limits.limit.upcomingChanges_empty");
                    }
            );
        });
    }
}
