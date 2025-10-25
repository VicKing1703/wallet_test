# Your Role

You are a **senior QA automation engineer on Java**, working on an **integration-testing framework** for a gambling wallet system.

Your task is to write **end-to-end tests** that validate business scenarios (player registration, bets, wins, limits) **across all system layers**:
- REST API (Public API, Manager API, CAP API)
- Message queues (NATS JetStream, Kafka)
- Cache (Redis)
- Database (MySQL via JPA)

A typical test validates business flows through multiple layers, e.g.: HTTP → NATS → Kafka → Redis → DB.

## Project Architecture

The framework is built on **multi-source test client** — a library with fluent API for interacting with all data sources.

**Key components:**
- **`DefaultTestSteps`** — pre-built steps for player registration and game session creation (used in 90% of tests)
- **Base test classes** (`BaseTest`, `BaseParameterizedTest`, `BaseNegativeParameterizedTest`) — contain Spring configuration, clients, utilities
- **Transport clients** (in `src/test/java/.../api/`) — wrappers over Feign, Kafka, NATS, Redis, JPA
- **Test suites** (in `src/test/java/.../tests/`) — end-to-end scenarios organized by business domains

## Repository Layout Quick Map

```text
/-wallet_test
├── build.gradle               # Root Gradle build that wires the wallet tests with the multi-source client module
├── settings.gradle            # Declares the composite build (wallet tests + multi-source client)
├── docs/                      # Additional domain documentation and architecture diagrams
├── multi-source-test-client/  # Shared client library for HTTP, Kafka, NATS, Redis, and DB access
│   ├── README.md              # DSL overview and usage examples for each client
│   ├── build.gradle           # Module-specific dependencies (Feign, Kafka, NATS, Redis, Awaitility)
│   └── src/
│       ├── main/java/com/testing/multisource/...   # Fluent client implementations & DTOs for multi-source polling
│       └── main/resources/                         # Module configuration defaults (if any)
├── src/
│   ├── main/java/com/uplatform/wallet_tests/config # Spring configuration, environment bootstrap, client wiring
│   ├── main/resources/                             # Test environment JSON templates & Spring config files
│   └── test/java/com/uplatform/wallet_tests/
│       ├── api/                                    # Transport clients (HTTP/Kafka/NATS/Redis/DB) for wallet tests
│       ├── allure/                                 # Shared Allure step helpers and annotations
│       ├── config/                                 # Base test classes, environment selectors, fixtures
│       └── tests/                                  # End-to-end scenarios (casino limits, registration, gambling, etc.)
├── build/                      # Generated build outputs (Gradle caches, compiled classes, reports)
└── README.md                  # High-level introduction and onboarding steps
```

---

## DefaultTestSteps

Use `DefaultTestSteps` to prepare players and game sessions in your tests. It handles all setup (registration, OTP, authentication, wallet creation, KYC) automatically.

### Available Methods

**`registerNewPlayer(BigDecimal adjustmentAmount)`** — registers player with initial balance

**Input:** `adjustmentAmount` — initial balance to credit (use `BigDecimal.ZERO` for no funding)
**Returns:** `RegisteredPlayerData` — contains auth token and wallet data
**When to use:** Most tests (90%) that need a player

```java
// With balance
ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(new BigDecimal("2000.00"));

// Without balance (convenience overload)
ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
```

**`registerNewPlayerWithKyc()`** — registers player with full KYC verification

**Input:** none
**Returns:** `RegisteredPlayerData` for fully verified player
**When to use:** Tests requiring KYC-complete players

```java
ctx.registeredPlayer = defaultTestSteps.registerNewPlayerWithKyc();
```

**`createGameSession(RegisteredPlayerData playerData)`** — launches game session

**Input:** `playerData` — player returned from registration
**Returns:** `GameLaunchData` — contains session identifiers and metadata
**When to use:** Tests covering gambling operations (bet, win, rollback)

```java
ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
```

### What DefaultTestSteps Does Internally

**`registerNewPlayer()`:**
1. Requests phone verification via FAPI
2. Fetches OTP code from Kafka
3. Verifies phone contact
4. Performs full registration (FAPI)
5. Fetches authorization token
6. Applies balance adjustment via CAP Admin API (if amount > 0)
7. Fetches final wallet state from Redis
8. Returns `RegisteredPlayerData`

**`createGameSession()`:**
1. Fetches available games via FAPI
2. Launches random game for player
3. Fetches game session from database
4. Returns `GameLaunchData`

### Return Types

**`RegisteredPlayerData`** — record containing:
```java
public record RegisteredPlayerData(
    ResponseEntity<TokenCheckResponse> authorizationResponse,
    WalletFullData walletData
)
```

**Key fields:**
- `authorizationResponse.getBody().getToken()` — Bearer token for API calls
- `walletData.playerUUID()` — player UUID
- `walletData.walletUUID()` — wallet UUID
- `walletData.balance()` — current balance
- `walletData.currency()` — wallet currency
- `walletData.limits()` — list of active limits (`LimitData`)
- `walletData.gambling()` — map of gambling transactions (key = transaction UUID)
- `walletData.isBlocked()` — wallet block status
- `walletData.isKYCUnverified()` — KYC verification status

**Usage example:**
```java
String token = ctx.registeredPlayer.authorizationResponse().getBody().getToken();
String walletUuid = ctx.registeredPlayer.walletData().walletUUID();
BigDecimal balance = ctx.registeredPlayer.walletData().balance();
```

**`GameLaunchData`** — record containing:
```java
public record GameLaunchData(
    WalletGameSession dbGameSession,
    ResponseEntity<LaunchGameResponseBody> launchGameResponse
)
```

**Key fields:**
- `dbGameSession.getGameSessionUuid()` — session UUID from database
- `dbGameSession.getWalletUuid()` — wallet UUID
- `launchGameResponse.getBody().url()` — game launch URL

**Important:** Always use `DefaultTestSteps` for player/session setup instead of manually calling FAPI/CAP endpoints.

---

## Test Types

All tests extend one of three base classes. Each provides Spring configuration and auto-wired clients.

### `BaseTest`
For single-scenario tests (non-parameterized).

**Features:**
- All clients auto-wired (`managerClient`, `publicClient`, `natsClient`, `kafkaClient`, `redisWalletClient`, `walletDatabaseClient`, etc.)
- `defaultTestSteps` available
- `utils` for signatures and helpers
- `@Execution(ExecutionMode.CONCURRENT)` — runs in parallel

**Example:**
```java
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
class MyTest extends BaseTest {
    @Test
    void test() {
        var player = defaultTestSteps.registerNewPlayer(new BigDecimal("100.00"));
        // test logic
    }
}
```

### `BaseParameterizedTest`
For parameterized positive tests (multiple scenarios with different inputs).

**Features:**
- Extends `BaseTest` — all clients available
- `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` — allows non-static `@MethodSource`

**Example:**
```java
class MyParameterizedTest extends BaseParameterizedTest {

    static Stream<Arguments> testData() {
        return Stream.of(
            Arguments.of(new BigDecimal("10.00"), "BET"),
            Arguments.of(new BigDecimal("20.00"), "WIN")
        );
    }

    @ParameterizedTest
    @MethodSource("testData")
    void test(BigDecimal amount, String type) {
        var player = defaultTestSteps.registerNewPlayer(amount);
        // test logic
    }
}
```

### `BaseNegativeParameterizedTest`
For parameterized negative tests (validation errors, edge cases).

**Features:**
- Extends `BaseParameterizedTest` — all features inherited
- Helper methods for error handling:
  - `executeExpectingError(Supplier, String)` — catches `FeignException` and parses error response
  - `assertValidationError(ValidationErrorResponse, int, String, Map)` — validates error structure

**Example:**
```java
class MyNegativeTest extends BaseNegativeParameterizedTest {

    static Stream<Arguments> invalidData() {
        return Stream.of(
            Arguments.of(new BigDecimal("-10.00"), 400, "Invalid amount"),
            Arguments.of(null, 400, "Amount is required")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidData")
    void testInvalidAmount(BigDecimal amount, int expectedCode, String expectedMessage) {
        var player = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);

        var error = executeExpectingError(
            () -> managerClient.bet(casinoId, signature, request),
            "Expected FeignException"
        );

        assertValidationError(error, expectedCode, expectedMessage, Map.of());
    }
}
```

### Negative Tests Patterns

When writing negative tests, use these patterns to structure error validation scenarios effectively.

**IMPORTANT: Negative tests MUST extend `BaseNegativeParameterizedTest`**, not `BaseParameterizedTest`.

#### Key Rules for Negative Tests

1. **Base class:** Always extend `BaseNegativeParameterizedTest`
2. **Constants naming:** Use `UPPER_SNAKE_CASE` for class-level constants (e.g., `INITIAL_BALANCE`, `BET_AMOUNT`, `CASINO_ID`)
3. **Error messages:** NEVER hardcode error messages in provider - use `expectedErrorCode.getMessage()` or remove message parameter entirely
4. **Description parameter:** Avoid adding extra description parameters - use `@ParameterizedTest(name = "тип = {0}")` instead
5. **Error validation:** For Manager API gambling errors, use `utils.parseFeignExceptionContent()` and dynamic message comparison

#### Consumer Pattern for Request Modification

Use `Consumer<RequestBody>` to modify valid requests into invalid ones. This pattern keeps test data DRY and makes scenarios readable.

**Structure:**
```java
static Stream<Arguments> negativeScenariosProvider() {
    return Stream.of(
        arguments(
            "описание сценария",                          // Test name in reports
            (Consumer<RequestBody>) req -> req.setField(invalidValue),  // Request modifier
            HttpStatus.BAD_REQUEST,                       // Expected HTTP status
            ERROR_CODE,                                   // Expected error code (enum)
            "expected error message substring"            // Expected error message
        )
    );
}
```

**Full example:**
```java
static Stream<Arguments> negativeBetScenariosProvider() {
    return Stream.of(
        arguments("без sessionToken",
            (Consumer<BetRequestBody>) req -> req.setSessionToken(null),
            HttpStatus.BAD_REQUEST,
            MISSING_TOKEN,
            "missing session token"),

        arguments("пустой sessionToken",
            (Consumer<BetRequestBody>) req -> req.setSessionToken(""),
            HttpStatus.BAD_REQUEST,
            MISSING_TOKEN,
            "missing session token"),

        arguments("отрицательный amount",
            (Consumer<BetRequestBody>) req -> req.setAmount(new BigDecimal("-1.0")),
            HttpStatus.BAD_REQUEST,
            VALIDATION_ERROR,
            "amount: value [-1] must be greater or equal than [0]"),

        arguments("roundId превышает 255 символов",
            (Consumer<BetRequestBody>) req -> req.setRoundId("a".repeat(256)),
            HttpStatus.BAD_REQUEST,
            VALIDATION_ERROR,
            "roundId: the length must be no more than 255")
    );
}

@ParameterizedTest(name = "{0}")
@MethodSource("negativeBetScenariosProvider")
void test(String description, Consumer<BetRequestBody> requestModifier,
          HttpStatus expectedStatus, GamblingErrors expectedErrorCode,
          String expectedMessageSubstring) {

    final class TestContext {
        BetRequestBody request;
    }
    final TestContext ctx = new TestContext();

    step("Подготовка некорректного запроса: " + description, () -> {
        // Build valid request first
        ctx.request = BetRequestBody.builder()
            .sessionToken(validSessionToken)
            .amount(validAmount)
            .transactionId(UUID.randomUUID().toString())
            .build();

        // Apply modification to make it invalid
        requestModifier.accept(ctx.request);
    });

    step("Manager API: Попытка некорректной ставки", () -> {
        var exception = assertThrows(FeignException.class,
            () -> managerClient.bet(casinoId, signature, ctx.request));

        var error = utils.parseFeignExceptionContent(exception, GamblingError.class);

        assertAll("Проверка деталей ошибки",
            () -> assertEquals(expectedStatus.value(), exception.status()),
            () -> assertEquals(expectedErrorCode.getCode(), error.code()),
            () -> assertTrue(error.message().toLowerCase()
                .contains(expectedMessageSubstring.toLowerCase()))
        );
    });
}
```

#### Error Handling: `executeExpectingError()` vs `utils.parseFeignExceptionContent()`

**Choose the right method based on the API:**

**For CAP API validation errors** (returns `ValidationErrorResponse`):
```java
var error = executeExpectingError(
    () -> capAdminClient.updateBlockers(playerUuid, authHeader, nodeId, request),
    "expected_exception"
);

assertValidationError(error, 400, "Validation error", expectedFieldErrors);
```

**For Manager/FAPI gambling errors** (returns custom error DTOs like `GamblingError`):
```java
var exception = assertThrows(FeignException.class,
    () -> managerClient.bet(casinoId, signature, request),
    "manager_api.bet.exception"
);

var error = utils.parseFeignExceptionContent(exception, GamblingError.class);

assertAll("Проверка деталей ошибки",
    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), exception.status(), "manager_api.error.status_code"),
    () -> assertEquals(expectedErrorCode.getCode(), error.code(), "manager_api.error.code"),
    () -> assertTrue(error.message().toLowerCase()
        .contains(expectedErrorCode.getMessage().toLowerCase()), "manager_api.error.message")
);
```

**IMPORTANT:** When comparing error messages from Manager API:
- Use `expectedErrorCode.getMessage()` instead of hardcoded strings
- Use `.toLowerCase().contains()` for flexible comparison
- NEVER pass hardcoded message strings in provider parameters

#### Grouping Error Scenarios

Group scenarios by error type for better organization:

```java
// Group 1: Request body validation
static Stream<Arguments> requestBodyValidationProvider() {
    return Stream.of(
        arguments("без sessionToken", modifierForSessionToken, ...),
        arguments("без transactionId", modifierForTransactionId, ...)
    );
}

// Group 2: Path parameter validation
static Stream<Arguments> pathParameterValidationProvider() {
    return Stream.of(
        arguments("невалидный UUID", "not-a-uuid", HttpStatus.BAD_REQUEST, ...),
        arguments("несуществующий UUID", UUID.randomUUID().toString(), HttpStatus.NOT_FOUND, ...)
    );
}

// Group 3: Authorization validation
static Stream<Arguments> authorizationValidationProvider() {
    return Stream.of(
        arguments("отсутствие заголовка", null),
        arguments("пустая строка", "")
    );
}
```

#### `@BeforeEach` for Test Isolation

**Always use `@BeforeEach` in negative tests** to ensure fresh data for each scenario:

```java
class MyNegativeTest extends BaseNegativeParameterizedTest {
    private RegisteredPlayerData registeredPlayer;

    @BeforeEach
    void registerPlayer() {
        // Fresh player for EACH test scenario - ensures isolation
        this.registeredPlayer = defaultTestSteps.registerNewPlayer();
    }

    @ParameterizedTest
    @MethodSource("negativeScenariosProvider")
    void test(String description, ...) {
        // Each iteration gets fresh player
        // No data pollution between scenarios
    }
}
```

**Why `@BeforeEach` for negative tests:**
- ✅ Complete isolation between test scenarios
- ✅ Prevents side effects from previous iterations
- ✅ Ensures consistent starting state
- ✅ Makes debugging easier (each scenario independent)

---

## Allure Annotations

All tests must be properly annotated for Allure reporting. These annotations organize tests in reports and enable filtering.

### Required Annotations

Every test class **must** have these annotations:

```java
@Severity(SeverityLevel.BLOCKER)      // or CRITICAL, NORMAL, MINOR, TRIVIAL
@Epic("Domain")                       // High-level category
@Feature("Functionality")             // Specific feature or endpoint
@Suite("Test Suite Name")             // Grouping for reports
@Tag("Category")                      // Tags for test execution filtering
class MyTest extends BaseTest {
    // ...
}
```

### Severity Levels

Choose severity based on business impact:

- **`BLOCKER`** — Core functionality, blocks multiple features (e.g., player registration, bet processing)
- **`CRITICAL`** — Major features, high business impact (e.g., balance adjustments, limits)
- **`NORMAL`** — Standard functionality (e.g., getting player info)
- **`MINOR`** — Edge cases, minor features
- **`TRIVIAL`** — UI/cosmetic issues

**Example:**
```java
@Severity(SeverityLevel.BLOCKER)     // Player can't register = system unusable
@Epic("Registration")
@Feature("Phone Verification")
```

### Epic Categories

Use consistent Epic names across the codebase:

- `Gambling` — bet, win, rollback, refund operations
- `Limits` — casino loss limit, deposit limit, single bet limit, turnover limit
- `CAP` — admin operations via CAP API
- `Payment` — deposits, withdrawals
- `Registration` — player registration flows
- `Betting` — iframe betting operations

### Feature Names

**For API endpoints:** Use the endpoint path
```java
@Feature("/bet")
@Feature("/win")
@Feature("/profile/limit/casino-loss")
```

**For business features:** Use descriptive names
```java
@Feature("CasinoLossLimit")
@Feature("Player Manual Block")
@Feature("Balance Adjustment")
```

### Suite Names

Use consistent suite naming pattern: `<Category>: <Scenario Type>`

**Positive scenarios:**
```java
@Suite("Позитивные сценарии: /bet")
@Suite("Позитивные сценарии: CasinoLossLimit")
```

**Negative scenarios:**
```java
@Suite("Негативные сценарии: /bet")
@Suite("Ручная корректировка баланса: Негативные сценарии")
```

**Specific features:**
```java
@Suite("Ручная блокировка игрока: Позитивные сценарии")
@Suite("Ручные блокировки гемблинга и беттинга: Позитивные сценарии")
```

### Tags

Use tags to filter test execution:

```java
@Tag("Gambling")           // Business domain
@Tag("Wallet")             // System component
@Tag("CasinoLossLimit")    // Specific feature
```

**Multiple tags:**
```java
@Tag("Gambling") @Tag("Wallet")
@Tag("Limits") @Tag("Wallet") @Tag("CasinoLossLimit")
```

### Optional Annotations

**`@DisplayName`** — human-readable test name (appears in reports):
```java
@DisplayName("Совершение ставки:")
@DisplayName("Создание, проверка и получение CasinoLossLimit:")
```

**`@Story`** — sub-feature grouping (rarely used in this codebase)

**`@Link`** — link to documentation or requirements:
```java
@Link(name = "JIRA-123", url = "https://jira.company.com/browse/JIRA-123")
```

**`@Issue`** — link to bug tracker:
```java
@Issue("JIRA-456")
```

### Complete Example

```java
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/bet")
@Suite("Позитивные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
@DisplayName("Совершение ставки:")
class BetParametrizedTest extends BaseParameterizedTest {

    @ParameterizedTest(name = "тип = {2} и сумма = {0}")
    @MethodSource("betAmountProvider")
    void test(BigDecimal amount, NatsGamblingTransactionOperation operation,
              NatsGamblingTransactionType type) {
        // Test implementation
    }
}
```

**Note:** The `@ParameterizedTest(name = "...")` annotation customizes how each parameter combination appears in reports. Use placeholders like `{0}`, `{1}`, `{2}` to reference parameter values.

---

## TestContext Pattern

Every test method should declare a local `TestContext` class to store test data, responses, and expected values. This keeps the test organized and makes data flow clear.

### Structure

```java
@ParameterizedTest
@MethodSource("testData")
void test(BigDecimal amount, String operation) {
    // 1. Extract config constants
    final String casinoId = HttpServiceHelper.getManagerCasinoId(configProvider.getEnvironmentConfig().getHttp());

    // 2. Define TestContext as local final class
    final class TestContext {
        // Input data from DefaultTestSteps
        RegisteredPlayerData registeredPlayer;
        GameLaunchData gameLaunchData;

        // Request bodies
        BetRequestBody betRequestBody;

        // Responses from each layer
        NatsMessage<NatsGamblingEventPayload> betEvent;
        WalletProjectionMessage kafkaProjection;

        // Expected values for assertions
        BigDecimal expectedBalance;
        BigDecimal expectedRestAmount;
    }

    // 3. Instantiate once per test
    final TestContext ctx = new TestContext();

    // 4. Initialize expected values
    ctx.expectedBalance = amount.subtract(new BigDecimal("10.00"));

    // 5. Use ctx throughout test steps
    step("Register player", () -> {
        ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(amount);
    });

    step("Place bet", () -> {
        ctx.betRequestBody = BetRequestBody.builder()
            .sessionToken(ctx.gameLaunchData.dbGameSession().getGameSessionUuid())
            .amount(new BigDecimal("10.00"))
            .build();

        // store response in ctx
        var response = managerClient.bet(casinoId, signature, ctx.betRequestBody);
        assertEquals(ctx.expectedBalance, response.getBody().balance());
    });

    step("Verify NATS event", () -> {
        ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
            .from(natsClient.buildWalletSubject(
                ctx.registeredPlayer.walletData().playerUUID(),
                ctx.registeredPlayer.walletData().walletUUID()))
            .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
            .with("uuid", ctx.betRequestBody.getTransactionId())
            .fetch();

        assertNotNull(ctx.betEvent);
    });
}
```

### Key Rules

1. **Declare as `final class TestContext`** inside the test method
2. **Instantiate as `final TestContext ctx = new TestContext()`**
3. **Store everything in `ctx`**:
   - Player data (`registeredPlayer`, `gameLaunchData`)
   - Request bodies (`betRequestBody`, `winRequestBody`)
   - Responses from each layer (`betEvent`, `kafkaProjection`)
   - Expected values for assertions (`expectedBalance`, `expectedRestAmount`)
4. **Use `ctx` fields in assertions** to make data flow traceable

### Why This Pattern?

- ✅ All test data in one place
- ✅ Clear data flow between steps
- ✅ Easy to see what was stored and when
- ✅ No test-level fields polluting the class scope

---

## Naming Conventions

Consistent naming makes tests maintainable and reports readable. Follow these conventions throughout the codebase.

### Test Class Names

**Pattern:** `<Feature><Scenario><TestType>`

**Test types:**
- `*Test` — single scenario (extends `BaseTest`)
- `*ParameterizedTest` or `*ParametrizedTest` — parameterized positive scenarios (extends `BaseParameterizedTest`)
- `*NegativeParametrizedTest` or `*NegativeParameterizedTest` — parameterized negative scenarios (extends `BaseNegativeParameterizedTest`)

**Examples:**
```java
BetParametrizedTest.java                      // Parameterized positive bet scenarios
BetNegativeParametrizedTest.java              // Parameterized negative bet scenarios
StartGameSessionTest.java                     // Single game session scenario
CasinoLossLimitCreateParameterizedTest.java   // Parameterized limit creation
```

### Provider Method Names

**Pattern:** `<domain><Type>Provider` or `<domain><Type>ScenariosProvider`

**Examples:**
```java
static Stream<Arguments> betAmountProvider() { ... }
static Stream<Arguments> negativeBetScenariosProvider() { ... }
static Stream<Arguments> balanceAdjustmentScenariosProvider() { ... }
```

### TestContext Variable Names

**Use camelCase and group by purpose:**

```java
final class TestContext {
    // 1. Player and session data (from DefaultTestSteps)
    RegisteredPlayerData registeredPlayer;
    GameLaunchData gameLaunchData;

    // 2. Request bodies (suffixed with Request/RequestBody)
    BetRequestBody betRequestBody;
    WinRequestBody winRequestBody;
    CreateBalanceAdjustmentRequest adjustmentRequest;

    // 3. Responses from each layer (suffixed with Event/Message/Response)
    NatsMessage<NatsGamblingEventPayload> betEvent;
    WalletProjectionMessage kafkaProjection;
    ResponseEntity<TokenCheckResponse> authorizationResponse;

    // 4. Expected values (prefixed with expected)
    BigDecimal expectedBalance;
    BigDecimal expectedRestAmount;
    int expectedStatusCode;
}
```

### Class-Level Constants

**Use UPPER_SNAKE_CASE for test-level constants:**

```java
class MyTest extends BaseParameterizedTest {
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("150.00");
    private static final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("100.00");
    private static final int EXPECTED_LIMIT_COUNT = 3;

    // Test-level variables (initialized in @BeforeAll)
    private String platformNodeId;
    private String currency;
}
```

---

## Assertions Best Practices

Consistent assertion patterns improve test readability and make Allure reports easier to navigate.

### When to Use `assertAll`

**Use `assertAll` when:**
- Checking multiple fields of the same object
- Validating a logical group of related assertions
- You want all checks to run even if one fails (complete validation)

```java
// Good - checking multiple fields of HTTP response
assertAll("Проверка статус-кода и тела ответа",
    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
    () -> assertEquals(expectedId, response.getBody().transactionId(), "manager_api.body.transactionId"),
    () -> assertEquals(0, expectedBalance.compareTo(response.getBody().balance()), "manager_api.body.balance")
);

// Good - checking nested object structure
var conversionInfo = betEvent.currencyConversionInfo();
var currencyRates = conversionInfo.currencyRates().get(0);
assertAll("Проверка полей внутри currency_conversion_info NATS payload",
    () -> assertEquals(0, amount.negate().compareTo(conversionInfo.gameAmount()), "currency_conversion_info.game_amount"),
    () -> assertEquals(currency, currencyRates.baseCurrency(), "currency_conversion_info.currency_rates.base_currency"),
    () -> assertEquals(currency, currencyRates.quoteCurrency(), "currency_conversion_info.currency_rates.quote_currency")
);
```

**Use single assertions when:**
- Performing a critical check that must pass before continuing
- The assertion is unrelated to others in the same step

```java
// Good - critical check before proceeding
assertNotNull(ctx.registeredPlayer, "default_step.registration");

// Good - independent checks in separate steps
step("NATS: Verify event received", () -> {
    assertNotNull(ctx.betEvent, "nats.event_received");
});
```

### BigDecimal Comparison

**ALWAYS use `compareTo` for BigDecimal — never use `equals`:**

```java
// ✅ Correct - ignores scale differences (100.00 == 100.0)
assertEquals(0, expectedAmount.compareTo(actualAmount), "amounts_equal");

// ❌ Wrong - fails on scale mismatch (100.00 != 100.0)
assertEquals(expectedAmount, actualAmount);

// ✅ Correct - for inequalities
assertTrue(actualAmount.compareTo(BigDecimal.ZERO) > 0, "amount_positive");
assertTrue(limit.compareTo(spent) >= 0, "limit_not_exceeded");
```

**Why:** `BigDecimal.equals()` compares both value AND scale (100.00 ≠ 100.0), causing false negatives.

### Assertion Message Conventions

**Use dotted notation: `"layer.component.field"`**

This pattern creates structured, hierarchical identifiers in Allure reports and stack traces.

**Format:** `<layer>.<component>.<field>` or `<layer>.<component>.<nested_field>`

**Layers:**
- `manager_api`, `fapi`, `cap` — HTTP APIs
- `nats` — NATS events
- `kafka` — Kafka messages
- `redis` — Redis aggregates
- `db` — Database records

**Examples:**
```java
// HTTP API assertions
assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");
assertEquals(expectedId, response.getBody().transactionId(), "manager_api.body.transactionId");
assertEquals(0, expectedBalance.compareTo(response.getBody().balance()), "fapi.body.balance");

// NATS payload assertions
assertEquals(transactionId, payload.uuid(), "nats.payload.uuid");
assertEquals(sessionId, payload.gameSessionUuid(), "nats.payload.game_session_uuid");
assertEquals(NatsTransactionDirection.WITHDRAW, payload.direction(), "nats.payload.direction");

// Nested fields
assertEquals(currency, currencyRates.baseCurrency(), "nats.payload.currency_conversion_info.base_currency");
assertEquals(amount, natsLimit.amount(), "nats.limit_changed_v2_event.limit.amount");

// Database assertions
assertEquals(playerUuid, transaction.getPlayerUuid(), "db.gpth.player_uuid");
assertEquals(expectedAmount, threshold.getAmount(), "db.ptw.amount");

// Redis assertions
assertEquals(sequence, aggregate.lastSeqNumber(), "redis.wallet.last_seq_number");
assertEquals(0, expectedBalance.compareTo(aggregate.balance()), "redis.wallet.balance");

// Kafka projections
assertTrue(utils.areEquivalent(kafkaMessage, natsEvent), "kafka.wallet_projection.equivalent_to_nats");
```

**Abbreviations:**
- `gpth` — `gambling_projection_transaction_history`
- `ptw` — `player_threshold_win`
- `ptd` — `player_threshold_deposit`

**Benefits:**
- ✅ Easy to locate failures in Allure reports
- ✅ Grep-friendly for debugging
- ✅ Hierarchical grouping in test output
- ✅ Consistent naming across all tests

---

## Full End-to-End Examples

Before diving into individual client APIs, review these complete test examples. Choose the pattern that matches your use case.

### 1. Simple E2E Flow: Manual Player Block

**File:** [`PlayerManualBlockTest.java`](src/test/java/com/uplatform/wallet_tests/tests/wallet/admin/PlayerManualBlockTest.java)

**Start here if you're new to the framework.** This test demonstrates a straightforward admin action (manual block) and validates the event propagation across four system layers.

**What it demonstrates:**
- Basic E2E validation: HTTP → Kafka → NATS → Kafka projection → Redis
- CAP Admin API usage with authorization headers
- `.unique()` for duplicate detection across Kafka and NATS
- Boolean state checks in Redis (`isBlocked`)
- Constants extraction pattern (`final String PLATFORM_NODE_ID = ...`)
- Nested Kafka field validation (`message.eventType`, `player.status`)

**Flow overview:**
```
CAP API: updatePlayerProperties (manuallyBlocked=true)
  ↓
Kafka: player.v1.account (PLAYER_STATUS_UPDATE → BLOCKED)
  ↓
NATS: wallet.{player}.{wallet}.wallet_blocked
  ↓
Kafka: wallet.v8.projectionSource (projection from NATS)
  ↓
Redis: wallet aggregate (isBlocked = true)
```

**Key snippet:**
```java
@Test
void shouldBlockPlayerAndPublishEvents() {
    final String PLATFORM_NODE_ID = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

    final class TestContext {
        RegisteredPlayerData registeredPlayer;
        UpdatePlayerPropertiesRequest updateRequest;
        PlayerStatusUpdateMessage statusUpdateMessage;
        NatsMessage<NatsWalletBlockedPayload> walletBlockedEvent;
    }
    final TestContext ctx = new TestContext();

    step("Register player", () -> {
        ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
    });

    step("CAP API: Block player manually", () -> {
        ctx.updateRequest = UpdatePlayerPropertiesRequest.builder()
            .manuallyBlocked(true)
            // ... other flags
            .build();

        var response = capAdminClient.updatePlayerProperties(
            ctx.registeredPlayer.walletData().playerUUID(),
            utils.getAuthorizationHeader(),
            PLATFORM_NODE_ID,
            platformUserId,
            platformUsername,
            ctx.updateRequest);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    });

    step("Kafka: Verify player status update", () -> {
        ctx.statusUpdateMessage = kafkaClient.expect(PlayerStatusUpdateMessage.class)
            .with("message.eventType", PlayerAccountEventType.PLAYER_STATUS_UPDATE.getValue())
            .with("player.externalId", ctx.registeredPlayer.walletData().playerUUID())
            .with("player.status", PlayerAccountStatus.BLOCKED.getCode())
            .unique()
            .fetch();
    });

    step("NATS: Verify wallet_blocked event", () -> {
        ctx.walletBlockedEvent = natsClient.expect(NatsWalletBlockedPayload.class)
            .from(natsClient.buildWalletSubject(playerUuid, walletUuid))
            .withType(NatsEventType.WALLET_BLOCKED.getHeaderValue())
            .unique()
            .fetch();
    });

    step("Redis: Verify wallet blocked state", () -> {
        var aggregate = redisWalletClient
            .key(walletUuid)
            .withAtLeast("LastSeqNumber", ctx.walletBlockedEvent.getSequence())
            .fetch();

        assertTrue(aggregate.isBlocked());
    });
}
```

---

### 2. Parametrized Positive Tests: Balance Adjustment

**File:** [`BalanceAdjustmentParametrizedTest.java`](src/test/java/com/uplatform/wallet_tests/tests/wallet/admin/BalanceAdjustmentParametrizedTest.java)

**Use this when testing multiple business scenarios with different parameter combinations.** This test validates 12 combinations of direction, operation type, and reason for balance adjustments.

**What it demonstrates:**
- `BaseParameterizedTest` usage
- `@BeforeAll` for test-level setup (extracting config constants once)
- `static Stream<Arguments>` provider with 12 parameter combinations
- `@ParameterizedTest(name = "{0}, {1}, {2}")` for readable Allure reports
- Dynamic expected value calculation based on test parameters
- Enum mapping utilities (`mapOperationTypeToNatsInt()`, `mapDirectionToNatsInt()`)
- Redis balance history validation (`balanceBefore` vs `balance`)
- All system layers: HTTP → NATS → Kafka projection → Redis

**Flow overview:**
```
CAP API: createBalanceAdjustment (INCREASE/DECREASE, operation, reason)
  ↓
NATS: wallet.{player}.{wallet}.balance_adjusted
  ↓
Kafka: wallet.v8.projectionSource (projection from NATS)
  ↓
Redis: wallet aggregate (balance, balanceBefore, lastSeqNumber updated)
```

**Key patterns:**

```java
class BalanceAdjustmentParametrizedTest extends BaseParameterizedTest {
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("150.00");
    private static final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("100.00");

    // Test-level fields initialized once
    private String platformNodeId;
    private String currency;

    @BeforeAll
    void setupGlobalTestContext() {
        var envConfig = configProvider.getEnvironmentConfig();
        this.platformNodeId = envConfig.getPlatform().getNodeId();
        this.currency = envConfig.getPlatform().getCurrency();
    }

    static Stream<Arguments> balanceAdjustmentScenariosProvider() {
        return Stream.of(
            arguments(DirectionType.INCREASE, OperationType.CORRECTION, ReasonType.MALFUNCTION),
            arguments(DirectionType.INCREASE, OperationType.DEPOSIT, ReasonType.OPERATIONAL_MISTAKE),
            arguments(DirectionType.DECREASE, OperationType.CORRECTION, ReasonType.BALANCE_CORRECTION),
            // ... 9 more combinations
        );
    }

    @ParameterizedTest(name = "{0}, {1}, {2}")
    @MethodSource("balanceAdjustmentScenariosProvider")
    void balanceAdjustmentTest(
        DirectionType direction,
        OperationType operationType,
        ReasonType reasonType
    ) {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            CreateBalanceAdjustmentRequest adjustmentRequest;
            NatsMessage<NatsBalanceAdjustedPayload> balanceAdjustedEvent;
            BigDecimal expectedBalanceAfterAdjustment;
        }
        final TestContext ctx = new TestContext();

        // Calculate expected value dynamically
        ctx.expectedBalanceAfterAdjustment = (direction == DirectionType.DECREASE)
            ? INITIAL_BALANCE.subtract(ADJUSTMENT_AMOUNT)
            : INITIAL_BALANCE.add(ADJUSTMENT_AMOUNT);

        step("Register player with initial balance", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(INITIAL_BALANCE);
        });

        step("CAP API: Create balance adjustment", () -> {
            ctx.adjustmentRequest = CreateBalanceAdjustmentRequest.builder()
                .amount(ADJUSTMENT_AMOUNT)
                .direction(direction)
                .operationType(operationType)
                .reason(reasonType)
                .build();

            var response = capAdminClient.createBalanceAdjustment(
                playerUuid, authHeader, platformNodeId, platformUserId, ctx.adjustmentRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        });

        step("NATS: Verify balance_adjusted event", () -> {
            ctx.balanceAdjustedEvent = natsClient.expect(NatsBalanceAdjustedPayload.class)
                .from(natsClient.buildWalletSubject(playerUuid, walletUuid))
                .withType(NatsEventType.BALANCE_ADJUSTED.getHeaderValue())
                .unique()
                .fetch();

            var payload = ctx.balanceAdjustedEvent.getPayload();
            var expectedAdjustment = (direction == DirectionType.DECREASE)
                ? ADJUSTMENT_AMOUNT.negate() : ADJUSTMENT_AMOUNT;

            assertAll(
                () -> assertEquals(0, expectedAdjustment.compareTo(payload.amount())),
                () -> assertEquals(mapOperationTypeToNatsInt(operationType), payload.operationType()),
                () -> assertEquals(mapDirectionToNatsInt(direction), payload.direction())
            );
        });

        step("Redis: Verify balance history", () -> {
            var aggregate = redisWalletClient
                .key(walletUuid)
                .withAtLeast("LastSeqNumber", ctx.balanceAdjustedEvent.getSequence())
                .fetch();

            assertAll(
                () -> assertEquals(0, INITIAL_BALANCE.compareTo(aggregate.balanceBefore())),
                () -> assertEquals(0, ctx.expectedBalanceAfterAdjustment.compareTo(aggregate.balance()))
            );
        });
    }
}
```

---

### 3. Parametrized Negative Tests: Blockers Validation

**File:** [`BlockersNegativeParametrizedTest.java`](src/test/java/com/uplatform/wallet_tests/tests/wallet/admin/BlockersNegativeParametrizedTest.java)

**Use this for validation and error handling scenarios.** This test verifies that the API correctly rejects invalid requests and returns structured error responses.

**What it demonstrates:**
- `BaseNegativeParameterizedTest` usage
- `@BeforeEach` for per-test setup (fresh player for each scenario)
- Multiple `@MethodSource` methods grouped by error type
- `executeExpectingError()` helper to catch and parse `FeignException`
- `assertValidationError()` with field-level error checking
- Different HTTP error codes: 400 (validation), 401 (auth), 404 (not found)
- Validation of request body, path parameters, and headers

**Test scenarios:**
1. **Request body validation (HTTP 400):** Missing required fields (`gamblingEnabled`, `bettingEnabled`)
2. **Path parameter validation (HTTP 400, 404):** Invalid UUID format, non-existent UUID
3. **Authorization validation (HTTP 401):** Missing or empty `Authorization` header

**Key patterns:**

```java
class BlockersNegativeParametrizedTest extends BaseNegativeParameterizedTest {

    private RegisteredPlayerData registeredPlayer;

    @BeforeEach
    void registerPlayer() {
        // Fresh player for each test scenario
        this.registeredPlayer = defaultTestSteps.registerNewPlayer();
    }

    // Group scenarios by error type
    static Stream<Arguments> requestBodyValidationProvider() {
        return Stream.of(
            arguments(
                "gamblingEnabled: отсутствует",
                UpdateBlockersRequest.builder()
                    .gamblingEnabled(null)
                    .bettingEnabled(true)
                    .build(),
                Map.of("gamblingEnabled", List.of("value.not.null"))
            ),
            arguments(
                "bettingEnabled: отсутствует",
                UpdateBlockersRequest.builder()
                    .gamblingEnabled(true)
                    .bettingEnabled(null)
                    .build(),
                Map.of("bettingEnabled", List.of("value.not.null"))
            )
        );
    }

    static Stream<Arguments> playerUuidValidationProvider() {
        return Stream.of(
            arguments(
                "рандомный UUID",
                UUID.randomUUID().toString(),
                HttpStatus.NOT_FOUND,
                "field:[wallet] msg:[not found]",
                Map.of()
            ),
            arguments(
                "невалидный формат UUID",
                "not-a-uuid",
                HttpStatus.BAD_REQUEST,
                "Validation message",
                Map.of()
            )
        );
    }

    static Stream<Arguments> authorizationValidationProvider() {
        return Stream.of(
            arguments("отсутствие заголовка", null),
            arguments("пустая строка", "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestBodyValidationProvider")
    void shouldReturnBadRequestWhenRequestBodyInvalid(
        String description,
        UpdateBlockersRequest invalidRequest,
        Map<String, List<String>> expectedFieldErrors
    ) {
        var error = executeExpectingError(
            () -> capAdminClient.updateBlockers(
                registeredPlayer.walletData().playerUUID(),
                utils.getAuthorizationHeader(),
                platformNodeId,
                invalidRequest
            ),
            "expected_exception"
        );

        assertValidationError(error, 400, "Validation error", expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("playerUuidValidationProvider")
    void shouldReturnErrorWhenPlayerUuidInvalid(
        String description,
        String invalidPlayerUuid,
        HttpStatus expectedStatus,
        String expectedMessage,
        Map<String, List<String>> expectedFieldErrors
    ) {
        var request = UpdateBlockersRequest.builder()
            .gamblingEnabled(true)
            .bettingEnabled(true)
            .build();

        var error = executeExpectingError(
            () -> capAdminClient.updateBlockers(invalidPlayerUuid, authHeader, nodeId, request),
            "expected_exception"
        );

        assertValidationError(error, expectedStatus.value(), expectedMessage, expectedFieldErrors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizationValidationProvider")
    void shouldReturnUnauthorizedWhenAuthorizationHeaderInvalid(
        String description,
        String invalidAuthorizationHeader
    ) {
        var request = UpdateBlockersRequest.builder()
            .gamblingEnabled(true)
            .bettingEnabled(true)
            .build();

        var error = executeExpectingError(
            () -> capAdminClient.updateBlockers(playerUuid, invalidAuthorizationHeader, nodeId, request),
            "expected_exception"
        );

        assertValidationError(error, 401, "Full authentication is required to access this resource.", Map.of());
    }
}
```

**Helper methods from `BaseNegativeParameterizedTest`:**

```java
// Executes a supplier expecting FeignException, parses the error response
protected ValidationErrorResponse executeExpectingError(
    Supplier<?> supplier,
    String assertionMessage
) {
    var exception = assertThrows(FeignException.class, supplier::get, assertionMessage);
    return objectMapper.readValue(exception.contentUTF8(), ValidationErrorResponse.class);
}

// Validates the error response structure
protected void assertValidationError(
    ValidationErrorResponse error,
    int expectedStatusCode,
    String expectedMessage,
    Map<String, List<String>> expectedFieldErrors
) {
    assertAll(
        () -> assertEquals(expectedStatusCode, error.statusCode()),
        () -> assertTrue(error.message().contains(expectedMessage)),
        () -> assertEquals(expectedFieldErrors, error.errors())
    );
}
```

---

## Client API Reference

This section covers **extending the framework** with new clients, DTOs, and configuration. For **writing tests**, refer to the E2E examples above.

### HTTP

**When to extend:** Adding new HTTP endpoints or services (beyond Manager, FAPI, CAP).

**Steps:**
1. **Create Feign client:** `@FeignClient` interface in `api/http/<service>/client`
2. **Register config:** Add service block in `configs/*.json` under `walletTests.http.services`
3. **Model DTOs:** Request (Lombok `@Builder`) and response (`record`) in `api/http/<service>/dto`
4. **Usage:** See E2E examples above for step patterns and assertions

**DTO templates:**
```java
// Request
@Data
@Builder
public class MyRequestBody {
    private String field;
}

// Response
public record MyResponseBody(String field) { }
```

**References:** [`ManagerClient`](src/test/java/com/uplatform/wallet_tests/api/http/manager/client/ManagerClient.java), [`configs/beta-09.json`](src/test/resources/configs/beta-09.json)

### NATS

**When to extend:** Adding new NATS event types or custom subjects.

**Steps:**
1. **Subject helper:** Use `natsClient.buildWalletSubject()` or add custom helper in `NatsClient`
2. **Configuration:** Verify `configs/*.json` has NATS block (hosts, streamName, timeouts)
3. **Model DTO:** Create `record` in `api/nats/dto` with `@JsonIgnoreProperties(ignoreUnknown = true)`
4. **Usage:** See E2E examples above for fluent API

**DTO template:**
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record MyNatsPayload(
    @JsonProperty("field_name") String fieldName,
    @JsonProperty("payload") Payload payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
        @JsonProperty("amount") BigDecimal amount
    ) { }
}
```

**Fluent API methods:**
- `.from(String)` — specify NATS subject
- `.withType(String)` — filter by event type field
- `.with(key, value)` — JSONPath filter (e.g., `"payload.uuid"`)
- `.unique()` — enforce uniqueness (default 400ms window)
- `.unique(Duration)` — custom duplicate window
- `.within(Duration)` — override timeout
- `.fetch()` — trigger polling and deserialize

**References:** [`NatsClient`](src/test/java/com/uplatform/wallet_tests/api/nats/NatsClient.java), [`configs/beta-09.json`](src/test/resources/configs/beta-09.json)

### Kafka

**When to extend:** Adding new Kafka topics or message types.

**How to add new Kafka topic:**

1. **Create DTO** in `api/kafka/dto/`:
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record MyKafkaMessage(
    @JsonProperty("field_name") String fieldName
) { }
```

2. **Register mapping** in `KafkaConsumerConfig`:
```java
@Bean
public KafkaTopicMappingRegistry kafkaTopicMappingRegistry() {
    Map<Class<?>, String> mappings = new HashMap<>();
    // ... existing mappings ...
    mappings.put(MyKafkaMessage.class, "my.topic.suffix");
    return new SimpleKafkaTopicMappingRegistry(mappings);
}
```

3. **Usage in tests:**
```java
var message = kafkaClient.expect(MyKafkaMessage.class)
    .with("fieldName", expectedValue)
    .fetch();
```

**Fluent API methods:**
- `.expect(Type.class)` — resolves topic via registry
- `.with(key, value)` — JSONPath filter (e.g., `"payload.uuid"`)
- `.unique()` — enforce uniqueness (default 400ms window)
- `.unique(Duration)` — custom duplicate window
- `.within(Duration)` — override timeout
- `.fetch()` — trigger polling and deserialize

**References:** [`KafkaConsumerConfig`](src/test/java/com/uplatform/wallet_tests/api/kafka/config/KafkaConsumerConfig.java), [`configs/beta-09.json`](src/test/resources/configs/beta-09.json)

### Redis

**When to extend:** Adding new Redis aggregates or key patterns.

**Steps:**
1. **Register type mapping:** Add `.register("name", new TypeReference<MyDto>() {})` in `RedisConfig`
2. **Configuration:** Verify `configs/*.json` has Redis block (host, port, database, timeouts)
3. **Model DTO:** Create `record` in `api/redis/model` with `@JsonIgnoreProperties(ignoreUnknown = true)`
4. **Usage:** See E2E examples above for fluent API (`.key()`, `.with()`, `.withAtLeast()`, `.within()`, `.fetch()`)

**Fluent API methods:**
- `.key(String)` — pin Redis key
- `.with(path, value)` or `.with(path, predicate, message)` — JsonPath assertions
- `.withAtLeast(path, number)` — numeric threshold check
- `.within(Duration)` — override Awaitility timeout
- `.fetch()` — trigger polling and deserialize

**References:** [`RedisConfig`](src/test/java/com/uplatform/wallet_tests/api/redis/config/RedisConfig.java), [`WalletFullData`](src/test/java/com/uplatform/wallet_tests/api/redis/model/WalletFullData.java)

### Database

**When to extend:** Adding new JPA entities, repository methods, or database client methods.

**Architecture:** Three database clients for different schemas:
- **WalletDatabaseClient** — wallet schema (transactions, thresholds, game sessions)
- **CoreDatabaseClient** — core schema (games, providers, wallets)
- **PlayerDatabaseClient** — player schema (account properties)

**Awaitility mechanism:** All `*OrFail` methods use polling with configurable retry timeout/interval. Records are automatically attached to Allure reports as JSON.

**Steps to add new query to existing client:**

1. **Create JPA Entity** (if not exists) in `api/db/entity/<schema>/`:
```java
@Entity
@Table(name = "table_name")
@Getter @Setter @NoArgsConstructor
public class MyEntity {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "field_name")
    private String fieldName;
}
```

2. **Create Repository** in `api/db/repository/<schema>/`:
```java
@Repository
public interface MyEntityRepository extends JpaRepository<MyEntity, String> {
    Optional<MyEntity> findByUuid(String uuid);

    // For custom queries use @Query with native SQL
    @Query(value = "SELECT * FROM table_name WHERE field = :value", nativeQuery = true)
    Optional<MyEntity> findByCustomCondition(@Param("value") String value);

    // For projections (partial data)
    @Query(value = "SELECT field1 AS field1, field2 AS field2 FROM table_name WHERE id = :id", nativeQuery = true)
    MyProjection findProjectionById(@Param("id") String id);
}

// Projection interface (if needed)
public interface MyProjection {
    String getField1();
    String getField2();
}
```

3. **Add method to DatabaseClient** (e.g., WalletDatabaseClient):
```java
private final MyEntityRepository myEntityRepository;

// Add to constructor
public WalletDatabaseClient(..., MyEntityRepository myEntityRepository) {
    super(attachmentService);
    this.myEntityRepository = myEntityRepository;
}

@Transactional(readOnly = true)
public MyEntity findMyEntityByUuidOrFail(String uuid) {
    String description = String.format("my entity record by UUID '%s'", uuid);
    String attachmentNamePrefix = String.format("My Entity [UUID: %s]", uuid);
    Supplier<Optional<MyEntity>> querySupplier = () -> myEntityRepository.findByUuid(uuid);
    return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
}
```

4. **Usage in tests:**
```java
var entity = walletDatabaseClient.findMyEntityByUuidOrFail(uuid);
assertAll(
    () -> assertEquals(expectedValue, entity.getFieldName()),
    () -> assertNotNull(entity.getId())
);
```

**WalletDatabaseClient methods:**
- `findTransactionByUuidOrFail(String uuid)` — gambling_projection_transaction_history by UUID
- `findThresholdByPlayerUuidOrFail(String playerUuid)` — player_threshold_win by player UUID
- `findDepositThresholdByPlayerUuidOrFail(String playerUuid)` — player_threshold_deposit by player UUID
- `findSingleGameSessionByPlayerUuidOrFail(String playerUuid)` — wallet game session by player UUID
- `findWalletByUuidOrFail(String walletUuid)` — wallet record by UUID
- `findLatestIframeHistoryByUuidOrFail(String uuid)` — latest betting iframe history (ORDER BY seq DESC)

**CoreDatabaseClient methods:**
- `findLatestGameSessionByPlayerUuidOrFail(String playerUuid)` — latest core game session (ORDER BY started_at DESC)
- `findGameByIdOrFail(int gameId)` — core game by ID
- `findWalletByIdOrFail(int walletId)` — core wallet by ID
- `findGameProviderByIdOrFail(int providerId)` — game provider by ID

**PlayerDatabaseClient methods:**
- `findAccountPropertiesByPlayerUuidOrFail(String playerUuid)` — all account properties for player
- `waitForAccountPropertyStatus(String playerUuid, String propertyName, int expectedStatus, Duration timeout)` — wait for specific property status with custom timeout

**Usage patterns:**
```java
// Standard fetch + assertAll (preferred)
var transaction = walletDatabaseClient.findTransactionByUuidOrFail(transactionId);
assertAll(
    () -> assertEquals(expectedUuid, transaction.getUuid()),
    () -> assertEquals(expectedAmount, transaction.getAmount())
);

// Custom timeout wait
var property = playerDatabaseClient.waitForAccountPropertyStatus(
    playerUuid, "REQUIRED_LIMITS_SET", 1, Duration.ofMinutes(2));
assertEquals(1, property.get("status"));

// Multi-client usage
ctx.coreGameSession = coreDatabaseClient.findLatestGameSessionByPlayerUuidOrFail(playerUuid);
ctx.gameProvider = coreDatabaseClient.findGameProviderByIdOrFail(ctx.coreGameSession.getGameProviderId());
```

**References:** [`WalletDatabaseClient`](src/test/java/com/uplatform/wallet_tests/api/db/WalletDatabaseClient.java), [`CoreDatabaseClient`](src/test/java/com/uplatform/wallet_tests/api/db/CoreDatabaseClient.java), [`PlayerDatabaseClient`](src/test/java/com/uplatform/wallet_tests/api/db/PlayerDatabaseClient.java)

## Important Rules

**NEVER:**
- Create new HTTP/NATS/Database clients — only extend existing ones
- Modify configuration files (`configs/*.json`) or `multi-source-test-client` module

**Priority of instructions:**
- When examples in this document differ from existing test code, **ALWAYS follow this document**
- This document represents the current best practices and standards
- Existing tests may contain outdated patterns — do not replicate them


