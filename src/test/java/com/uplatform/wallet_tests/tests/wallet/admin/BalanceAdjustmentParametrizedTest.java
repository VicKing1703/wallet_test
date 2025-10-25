package com.uplatform.wallet_tests.tests.wallet.admin;

import com.testing.multisource.api.nats.dto.NatsMessage;
import com.testing.multisource.config.modules.http.HttpServiceHelper;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsBalanceAdjustedPayload;
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

import java.math.BigDecimal;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.NatsEnumMapper.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.NAME;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный параметризованный тест, проверяющий позитивные сценарии создания ручных корректировок баланса через CAP API:
 * {@code POST /_cap/api/v1/wallet/{playerUUID}/create-balance-adjustment}.
 *
 * <p><b>Идея теста:</b> Продемонстрировать полную сквозную надежность системы при выполнении административных операций
 * по корректировке баланса. Каждый успешный API-запрос должен инициировать корректную цепочку событий: от отправки
 * сообщения в NATS до финального обновления состояния кошелька в Redis. Тест гарантирует, что система не только
 * принимает запрос, но и правильно обрабатывает его на всех уровнях, обеспечивая консистентность данных.</p>
 *
 * <p><b>Сценарии тестирования сгруппированы по комбинациям параметров:</b></p>
 * <ul>
 *   <li><b>Направление ({@code direction}):</b> Увеличение ({@code INCREASE}) и уменьшение ({@code DECREASE}) баланса.</li>
 *   <li><b>Тип операции ({@code operationType}):</b> Различные типы, такие как коррекция, депозит, подарок, кэшбэк и др.</li>
 *   <li><b>Причина ({@code reason}):</b> Разные причины, включая технический сбой, операционную ошибку и др.</li>
 * </ul>
 *
 * <p><b>Последовательность действий для каждого набора параметров:</b></p>
 * <ol>
 *   <li>Регистрация нового игрока с начальным балансом.</li>
 *   <li>Отправка запроса в CAP API для создания ручной корректировки баланса с заданными параметрами.</li>
 *   <li>Проверка успешного ответа от CAP API (HTTP 200 OK).</li>
 *   <li>Прослушивание и валидация NATS-события типа {@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType#BALANCE_ADJUSTED},
 *       подтверждающего выполнение корректировки.</li>
 *   <li>Проверка того, что NATS-событие было успешно спроецировано в Kafka-топик {@code wallet.v8.projectionSource}.</li>
 *   <li>Проверка конечного состояния кошелька в Redis, включая итоговый баланс, предыдущий баланс и номер последовательности.</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Запрос на корректировку баланса выполняется успешно для всех комбинаций параметров.</li>
 *   <li>В NATS публикуется событие {@code balance_adjusted} с корректными данными, соответствующими запросу.</li>
 *   <li>Событие из NATS корректно дублируется в проекционный топик Kafka {@code wallet.v8.projectionSource}.</li>
 *   <li>Состояние кошелька в Redis обновляется в соответствии с направлением и суммой корректировки.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("Управление игроком")
@Suite("Ручная корректировка баланса: Позитивные сценарии")
@Tag("Wallet") @Tag("CAP")
class BalanceAdjustmentParametrizedTest extends BaseParameterizedTest {
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("150.00");
    private static final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("100.00");

    private String platformNodeId;
    private String currency;
    private String platformUserId;
    private String platformUsername;

    @BeforeAll
    void setupGlobalTestContext() {
        var envConfig = configProvider.getEnvironmentConfig();
        this.platformNodeId = envConfig.getPlatform().getNodeId();
        this.currency = envConfig.getPlatform().getCurrency();
        this.platformUserId = HttpServiceHelper.getCapPlatformUserId(envConfig.getHttp());
        this.platformUsername = HttpServiceHelper.getCapPlatformUsername(envConfig.getHttp());
    }

    static Stream<Arguments> balanceAdjustmentScenariosProvider() {
        return Stream.of(
                arguments(DirectionType.INCREASE, OperationType.CORRECTION, ReasonType.MALFUNCTION),
                arguments(DirectionType.INCREASE, OperationType.DEPOSIT, ReasonType.OPERATIONAL_MISTAKE),
                arguments(DirectionType.INCREASE, OperationType.GIFT, ReasonType.BALANCE_CORRECTION),
                arguments(DirectionType.INCREASE, OperationType.CASHBACK, ReasonType.OPERATIONAL_MISTAKE),
                arguments(DirectionType.INCREASE, OperationType.TOURNAMENT_PRIZE, ReasonType.MALFUNCTION),
                arguments(DirectionType.INCREASE, OperationType.JACKPOT, ReasonType.BALANCE_CORRECTION),
                arguments(DirectionType.DECREASE, OperationType.CORRECTION, ReasonType.BALANCE_CORRECTION),
                arguments(DirectionType.DECREASE, OperationType.WITHDRAWAL, ReasonType.OPERATIONAL_MISTAKE),
                arguments(DirectionType.DECREASE, OperationType.GIFT, ReasonType.MALFUNCTION),
                arguments(DirectionType.DECREASE, OperationType.REFERRAL_COMMISSION, ReasonType.OPERATIONAL_MISTAKE),
                arguments(DirectionType.DECREASE, OperationType.TOURNAMENT_PRIZE, ReasonType.BALANCE_CORRECTION),
                arguments(DirectionType.DECREASE, OperationType.JACKPOT, ReasonType.MALFUNCTION)
        );
    }

    @ParameterizedTest(name = "{0}, {1}, {2}")
    @MethodSource("balanceAdjustmentScenariosProvider")
    @DisplayName("Создание ручной корректировки баланса:")
    void balanceAdjustmentTest(
            DirectionType direction,
            OperationType operationType,
            ReasonType reasonType
    ) {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            CreateBalanceAdjustmentRequest adjustmentRequest;
            NatsMessage<NatsBalanceAdjustedPayload> balanceAdjustedEvent;
            WalletProjectionMessage walletProjectionMessage;
            BigDecimal expectedBalanceAfterAdjustment;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedBalanceAfterAdjustment = (direction == DirectionType.DECREASE)
                ? INITIAL_BALANCE.subtract(ADJUSTMENT_AMOUNT)
                : INITIAL_BALANCE.add(ADJUSTMENT_AMOUNT);

        step("Default Step: Регистрация нового пользователя с начальным балансом", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_BALANCE);
            assertNotNull(ctx.registeredPlayer.walletData(), "default_step.registration.wallet_data");
        });

        step("CAP API: Создание ручной корректировки баланса", () -> {
            ctx.adjustmentRequest = CreateBalanceAdjustmentRequest.builder()
                    .currency(currency)
                    .amount(ADJUSTMENT_AMOUNT)
                    .reason(reasonType)
                    .operationType(operationType)
                    .direction(direction)
                    .comment(get(NAME))
                    .build();

            var response = capAdminClient.createBalanceAdjustment(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    platformUserId,
                    ctx.adjustmentRequest);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.create_balance_adjustment.status_code");
        });

        step("NATS: Проверка события balance_adjusted", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.walletData().playerUUID(),
                    ctx.registeredPlayer.walletData().walletUUID());

            ctx.balanceAdjustedEvent = natsClient.expect(NatsBalanceAdjustedPayload.class)
                    .from(subject)
                    .withType(NatsEventType.BALANCE_ADJUSTED.getHeaderValue())
                    .with("comment", ctx.adjustmentRequest.getComment())
                    .unique()
                    .fetch();

            var payload = ctx.balanceAdjustedEvent.getPayload();
            var expectedAdjustment = (direction == DirectionType.DECREASE)
                    ? ADJUSTMENT_AMOUNT.negate() : ADJUSTMENT_AMOUNT;

            assertAll("Проверка полей NATS-события balance_adjusted",
                    () -> assertNotNull(payload.uuid(), "nats.balance_adjusted.uuid"),
                    () -> assertEquals(ctx.adjustmentRequest.getCurrency(), payload.currency(), "nats.balance_adjusted.currency"),
                    () -> assertEquals(0, expectedAdjustment.compareTo(payload.amount()), "nats.balance_adjusted.amount"),
                    () -> assertEquals(mapOperationTypeToNatsInt(operationType), payload.operationType(), "nats.balance_adjusted.operation_type"),
                    () -> assertEquals(mapDirectionToNatsInt(direction), payload.direction(), "nats.balance_adjusted.direction"),
                    () -> assertEquals(mapReasonToNatsInt(reasonType), payload.reason(), "nats.balance_adjusted.reason"),
                    () -> assertEquals(ctx.adjustmentRequest.getComment(), payload.comment(), "nats.balance_adjusted.comment"),
                    () -> assertEquals(platformUserId, payload.userUuid(), "nats.balance_adjusted.user_uuid"),
                    () -> assertEquals(platformUsername, payload.userName(), "nats.balance_adjusted.user_name")
            );
        });

        step("Kafka: Проверка события balance_adjusted в топике wallet.v8.projectionSource", () -> {
            ctx.walletProjectionMessage = kafkaClient.expect(WalletProjectionMessage.class)
                    .with("type", ctx.balanceAdjustedEvent.getType())
                    .with("seq_number", ctx.balanceAdjustedEvent.getSequence())
                    .unique()
                    .fetch();

            assertAll("Проверка полей Kafka-сообщения, спроецированного из NATS",
                    () -> assertEquals(ctx.balanceAdjustedEvent.getType(), ctx.walletProjectionMessage.type(), "kafka.balance_adjusted.type"),
                    () -> assertEquals(ctx.balanceAdjustedEvent.getSequence(), ctx.walletProjectionMessage.seqNumber(), "kafka.balance_adjusted.seq_number"),
                    () -> assertEquals(ctx.registeredPlayer.walletData().walletUUID(), ctx.walletProjectionMessage.walletUuid(), "kafka.balance_adjusted.wallet_uuid"),
                    () -> assertEquals(ctx.balanceAdjustedEvent.getTimestamp().toEpochSecond(), ctx.walletProjectionMessage.timestamp(), "kafka.balance_adjusted.timestamp")
            );
        });

        step("Redis (Wallet): Проверка состояния кошелька после корректировки", () -> {
            assertNotNull(ctx.balanceAdjustedEvent, "context.balance_adjusted_event");

            var aggregate = redisWalletClient
                    .key(ctx.registeredPlayer.walletData().walletUUID())
                    .withAtLeast("LastSeqNumber", ctx.balanceAdjustedEvent.getSequence())
                    .fetch();

            assertAll("Проверка агрегата кошелька в Redis после корректировки",
                    () -> assertEquals(ctx.balanceAdjustedEvent.getSequence(), aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, INITIAL_BALANCE.compareTo(aggregate.balanceBefore()), "redis.wallet.balance_before"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterAdjustment.compareTo(aggregate.balance()), "redis.wallet.balance")
            );
        });
    }
}