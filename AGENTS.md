# Project Overview

This repository contains an integration-testing framework for a wallet system that orchestrates test flows across multiple backend services. The centerpiece is a multi-source test client capable of interacting with HTTP APIs, Kafka, NATS, Redis, and relational databases to validate gambling wallet scenarios end-to-end.

The architecture revolves around the multi-source test client which provides fluent APIs for each data source. Test suites extend shared base classes that encapsulate environment configuration, default setup steps, and cross-system assertions. All tests rely on `DefaultTestSteps` to prepare player entities and supporting data. Each test verifies business flows—such as player registration, wallet adjustments, game sessions, and financial transactions—across all integrated systems.

For a condensed introduction to the DSL and configuration model, review [`multi-source-test-client/README.md`](multi-source-test-client/README.md). It demonstrates how `.expect()/.with()/.fetch()` calls map to Kafka/NATS polling, how Redis/DB clients rely on Awaitility, and what a minimal `configs/local.json` should contain.

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

Use this map to immediately locate the artifacts you need:
- **Need a new client?** Browse the transport wrappers in `src/test/java/.../api` (HTTP, Kafka, NATS, Redis, DB) and mirror their multi-source conventions.
- **Need to adjust default steps or configuration?** Look under `src/main/java/.../config` and `src/main/resources`.
- **Need reusable multi-source expectations?** Dive into `multi-source-test-client/src/main/java/com/testing/multisource` for the fluent DSL building blocks.
- **Need scenario inspiration?** Browse `src/test/java/.../tests`—each package mirrors a business capability and uses the cross-system validation checklist outlined below.

---

## Default Test Steps (CRITICAL)
Every test **must** rely on the `DefaultTestSteps` helper when preparing players or sessions. The following describes the mandatory flows in detail and shows how existing tests already consume them.

### 2.1 `registerNewPlayer(BigDecimal adjustmentAmount)`
**Purpose:** Fully registers a new player and optionally adjusts their starting balance.

**Detailed steps:**
1. Initiate player registration via the Public API with phone number submission.
2. Retrieve the OTP code emitted to Kafka for the provided phone number.
3. Confirm the phone number using the OTP through the Public API.
4. Submit full registration data (profile, credentials, contact details).
5. Authenticate the player via the Public API to obtain an access token.
6. Fetch the freshly created wallet record from Redis to confirm initial state.
7. If `adjustmentAmount > 0`, invoke the CAP API to credit the player’s wallet by the specified amount.
8. Store the resulting CAP transaction details for later verification.
9. Cancel the KYC verification requirement through the back-office or CAP API.
10. Persist the player context (token, wallet ID, and any adjustments) inside `RegisteredPlayerData`.
11. Return the fully populated `RegisteredPlayerData` instance to the caller.

**Return:** `RegisteredPlayerData` containing the authorization token and wallet data.

**When to use:** In **90%** of tests for baseline player preparation where a funded wallet is required.

> **Example in production tests:** `CasinoLossLimitWhenBetFromGambleParameterizedTest` starts with
> ```java
> ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(new BigDecimal("2000.00"));
> ```
> The returned `RegisteredPlayerData` is later reused for Manager API calls, NATS assertions, and Redis checks. See [`src/test/java/com/uplatform/wallet_tests/tests/wallet/limit/casino_loss/CasinoLossLimitWhenBetFromGambleParameterizedTest.java`](src/test/java/com/uplatform/wallet_tests/tests/wallet/limit/casino_loss/CasinoLossLimitWhenBetFromGambleParameterizedTest.java).

### 2.2 `registerNewPlayer()`
**Purpose:** Registers a new player without applying any balance adjustments.

**Steps:** Executes the same flow as above but skips the CAP balance adjustment. The resulting wallet balance is zero.

**Return:** `RegisteredPlayerData` with zero-balance wallet.

**When to use:** Whenever the scenario does not require pre-funded balance.

### 2.3 `registerNewPlayerWithKyc()`
**Purpose:** Fully registers a player and completes KYC verification.

**Detailed steps:**
1. Perform all steps from `registerNewPlayer(BigDecimal adjustmentAmount)` (including optional balance adjustment logic).
2. Upload identity documents via the KYC service using the player’s token.
3. Trigger identity verification review through the CAP API.
4. Poll the KYC service until verification status is updated.
5. Verify the player’s email through the Public API using the emailed token.
6. Submit mandatory betting limits (SingleBet and Turnover) via the limits API.
7. Confirm the limits payload is accepted in Kafka projections.
8. Wait for the player record in the database to reach `REQUIRED_LIMITS_SET` status using Awaitility.
9. Update CAP verification status to reflect completed KYC.
10. Retrieve the final player aggregate from Redis to ensure the verified flags are set.
11. Store proof of limit configuration for later assertions.
12. Persist the enriched context inside `RegisteredPlayerData` (token, wallet, verification statuses).
13. Return the verified `RegisteredPlayerData`.

**Return:** `RegisteredPlayerData` for a fully verified player.

**When to use:** Scenarios requiring KYC-complete players.

> **Example in production tests:** `FullRegistrationDebugTest` verifies the full KYC path via
> ```java
> ctx.registeredPlayer = defaultTestSteps.registerNewPlayerWithKyc();
> ```
> Only success criteria are asserted, because all heavy lifting (Kafka polling, Redis lookup, CAP interaction) already lives inside the default step. See [`src/test/java/com/uplatform/wallet_tests/tests/wallet/registration/FullRegistrationDebugTest.java`](src/test/java/com/uplatform/wallet_tests/tests/wallet/registration/FullRegistrationDebugTest.java).

### 2.4 `createGameSession(RegisteredPlayerData playerData)`
**Purpose:** Launches a game session for the given player.

**Steps:**
1. Fetch the list of available games via the Public or CAP API.
2. Start a random game through the game launch API using the player’s token.
3. Retrieve the session details from the database to confirm creation.

**Return:** `GameLaunchData` containing session identifiers and metadata.

**When to use:** Tests covering gambling operations such as bet, win, and rollback flows.

> **Example in production tests:** Immediately after registering a player, `CasinoLossLimitWhenBetFromGambleParameterizedTest` executes
> ```java
> ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
> ```
> The returned session is then consumed when sending Manager API bets and for Redis aggregate verification.

---

## Code Style & Patterns
- **Test class structure:** Each test class extends `BaseTest` or `BaseParameterizedTest` and follows Arrange–Act–Assert sections separated by comments or Allure steps. Review the `@Suite`-annotated classes under `src/test/java/com/uplatform/wallet_tests/tests/wallet/limit/casino_loss` for the canonical layout.
- **Inner `TestData/TestContext` classes:** Define a `private static final class TestData` (or `TestContext`) that aggregates input fixtures and expected results. Instantiate once per test method. See the inline `TestContext` in `CasinoLossLimitWhenBetFromGambleParameterizedTest` for a real template.
- **Assertion naming:** Use descriptive assertion messages (`assertEquals(..., "context.key")`, `assertThat(...).as("context")`). Keep the dotted notation that maps to monitoring dashboards.
- **Allure steps:** Wrap each logical action in `step("Description", () -> { ... });`. Sub-steps are encouraged for NATS/Kafka validation like in the CasinoLoss tests.
- **Parameterized tests:** Prefer JUnit parameterized tests for scenario variations, keeping each parameter set isolated. Reuse `@MethodSource` patterns as shown in the casino-loss suite.

## Testing Guidelines
- Write tests that are self-contained and idempotent.
- Apply the isolation pattern: **every parameter set must use a new player** to avoid cross-test contamination.
- Verification order must be: **API → NATS → Kafka → Redis → DB**. The casino-loss tests demonstrate this sequence.
- Use `assertAll()` to group related assertions while keeping each check independent. Observe the Redis verification step where multiple limit fields are asserted together.

## Multi-Source Test Client Usage

The DSL is shared across clients. The README’s quick-start snippet shows how to wire a Spring Boot test with `FapiClient`, `KafkaClient`, `NatsClient`, Redis, and DB clients. Borrow that structure whenever you need direct client access inside test steps.

### Concrete snippets from production tests

Use the following copy-ready fragments to mirror the canonical verification order (HTTP → NATS → Kafka → Redis → DB). Each example links back to the live tests so you can inspect the surrounding assertions and context objects.

#### HTTP (Feign)
When you need a new HTTP step, follow the same recipe used across the casino-loss and CAP admin tests.

1. **Create (or reuse) a Feign client.** Declare an interface annotated with `@FeignClient` and map each endpoint. Place new clients under `src/test/java/com/uplatform/wallet_tests/api/http/<service>/client` so they sit next to the existing CAP/FAPI/Manager wrappers. For example, [`ManagerClient`](src/test/java/com/uplatform/wallet_tests/api/http/manager/client/ManagerClient.java) exposes the gambling endpoints:
   ```java
   @FeignClient(name = "managerClient", url = "${app.api.manager.base-url}")
   public interface ManagerClient {
       @PostMapping("/_core_gas_processing/bet")
       ResponseEntity<GamblingResponseBody> bet(
               @RequestHeader("X-Casino-Id") String casinoId,
               @RequestHeader("Signature") String signature,
               @RequestBody BetRequestBody request);
       // ... other endpoints
   }
   ```
   The `${app.api.manager.base-url}` placeholder is resolved from the JSON config, so the same client works on any environment.

2. **Register configuration.** Extend the active environment file under `src/test/resources/configs`. The beta profile already defines URLs, secrets, and casino identifiers:
   ```json
   "http": {
     "services": {
       "manager": {
         "baseUrl": "https://beta-09.b2bdev.pro",
         "secret": "…",
         "casinoId": "…"
       }
     }
   }
   ```
   Keep secrets inside the `walletTests.http.services` section when the test needs them (see [`configs/beta-09.json`](src/test/resources/configs/beta-09.json)). New services follow the same structure—`baseUrl` for Feign, optional credentials/secrets for request builders.

3. **Model request/response DTOs.** Place them under `src/test/java/com/uplatform/wallet_tests/api/http/<service>/dto` so tests discover them through package scanning alongside the client. Use Lombok builders for **request** payloads, and declare every **response** DTO as a Java `record` so serialization stays immutable and matches the rest of the suite. When documenting a response, describe each field so future assistants know how to assert it:
   ```java
   @Data
   @Builder
   public class BetRequestBody {
       private String sessionToken;
       private BigDecimal amount;
       private String transactionId;
       private NatsGamblingTransactionOperation type;
       private String roundId;
       private Boolean roundClosed;
   }

   /**
    * Snapshot returned by Manager gambling endpoints.
    * @param balance Current wallet balance after the operation (BigDecimal, scale already normalized by the service).
    * @param transactionId Idempotency key echoed back by the Manager API.
    */
   public record GamblingResponseBody(BigDecimal balance, String transactionId) { }
   ```
   Reference the ready-made manager DTOs in [`api/http/manager/dto`](src/test/java/com/uplatform/wallet_tests/api/http/manager/dto) when sculpting new payloads.

4. **Wrap the call into a final step.** Compose an Allure step that injects the client, populates the DTO, signs the request, and asserts the response. `CasinoLossLimitUpdateAfterBetParameterizedTest` shows the full pattern:
   ```java
   step("Manager API: place bet", () -> {
       var request = BetRequestBody.builder()
               .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
               .amount(betAmount)
               .transactionId(UUID.randomUUID().toString())
               .type(NatsGamblingTransactionOperation.BET)
               .roundId(UUID.randomUUID().toString())
               .roundClosed(false)
               .build();

       var response = managerClient.bet(
               casinoId,
               utils.createSignature(ApiEndpoints.BET, request),
               request);

       assertAll("manager_api.bet.response",
               () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status"),
               () -> assertEquals(request.getTransactionId(), response.getBody().transactionId(),
                       "manager_api.bet.body.transactionId"),
               () -> assertEquals(0, ctx.expectedBalanceAfterBet.compareTo(response.getBody().balance()),
                       "manager_api.bet.body.balance"));
   });
   ```
   After the HTTP assertion passes, continue with the NATS/Kafka/Redis/DB checks to complete the cross-system validation.

#### NATS
The same test waits for the `betted_from_gamble` event by chaining `.from()`, `.withType()`, and JSON-path filters:

```java
ctx.betEvent = natsClient.expect(NatsGamblingEventPayload.class)
        .from(natsClient.buildWalletSubject(playerUuid, walletUuid))
        .withType(NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue())
        .with("$.uuid", ctx.betRequestBody.getTransactionId())
        .fetch();

assertNotNull(ctx.betEvent, "nats.betted_from_gamble_event");
```
The casino-loss suite contains multiple variations; start with the `bet` path linked above.

#### Kafka
`BetParametrizedTest` demonstrates the Kafka projection check, ensuring the message sequence aligns with the NATS event:

```java
var kafkaMessage = kafkaClient.expect(WalletProjectionMessage.class)
        .with("seq_number", ctx.betEvent.getSequence())
        .fetch();

assertTrue(utils.areEquivalent(kafkaMessage, ctx.betEvent), "wallet.v8.projectionSource");
```
Reference [`src/test/java/com/uplatform/wallet_tests/tests/wallet/gambling/bet/BetParametrizedTest.java`](src/test/java/com/uplatform/wallet_tests/tests/wallet/gambling/bet/BetParametrizedTest.java).

#### Redis
The same bet test uses the Redis client to wait for the aggregate to catch up to the NATS sequence and assert individual fields:

```java
var aggregate = redisWalletClient
        .key(walletUuid)
        .withAtLeast("LastSeqNumber", sequence)
        .fetch();

assertAll("redis.wallet.aggregate_validation",
        () -> assertEquals(sequence, aggregate.lastSeqNumber(), "redis.wallet.last_seq_number"),
        () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.balance()), "redis.wallet.balance"),
        () -> assertTrue(aggregate.gambling().containsKey(transactionUuid), "redis.wallet.gambling.containsKey")
);
```
Inspect the Redis step in [`BetParametrizedTest`](src/test/java/com/uplatform/wallet_tests/tests/wallet/gambling/bet/BetParametrizedTest.java) for the full assertion list.

#### Database
Finalize the flow by fetching persisted entities via Awaitility-backed helpers. `BetParametrizedTest` verifies both the transaction history and threshold tables:

```java
var transaction = walletDatabaseClient.findTransactionByUuidOrFail(ctx.betRequestBody.getTransactionId());

assertAll("db.gpth.validation",
        () -> assertEquals(ctx.betEvent.getPayload().getUuid(), transaction.getUuid(), "db.gpth.uuid"),
        () -> assertEquals(ctx.registeredPlayer.getWalletData().playerUUID(), transaction.getPlayerUuid(),
                "db.gpth.player_uuid"),
        () -> assertEquals(ctx.betEvent.getSequence(), transaction.getSeqnumber(), "db.gpth.seqnumber")
);
```
Additional DB helpers such as `findThresholdByPlayerUuidOrFail` are exercised later in the same method.

> **Quick copy-paste template (from README):**
> ```java
> @SpringBootTest
> public class QuickStartTest {
>     @Autowired private FapiClient fapiClient;
>     @Autowired private KafkaClient kafkaClient;
>     @Autowired private NatsClient natsClient;
>     @Autowired private GenericRedisClient redisWalletClient;
>     @Autowired private WalletDatabaseClient walletDatabaseClient;
>
>     @Test
>     void shouldIntegrateWithAllSystems() {
>         WalletResponse wallet = fapiClient.getWallet(playerId, token);
>         LimitMessage kafkaMsg = kafkaClient.expect(LimitMessage.class)
>                 .with("playerId", playerId)
>                 .fetch();
>         // ... continue with NATS/Redis/DB checks
>     }
> }
> ```

## Cross-System Validation Pattern
Always follow this pattern:
1. Execute the action via HTTP API.
2. Verify corresponding NATS event.
3. Confirm Kafka projection update.
4. Validate Redis aggregate state.
5. Inspect database records for final consistency.

`CasinoLossLimitWhenBetFromGambleParameterizedTest` hits every layer in this exact order. Use it as a checklist when designing new scenarios.

## Configuration Management
- Environment configuration is JSON-based; select the correct file per environment (`src/test/resources/configs/*.json`).
- Configurations propagate to all system clients via the base test classes; do not override manually.
- For new environments copy the schema from the README’s `configs/local.json` example and adjust connection details only.

## Important Rules
- **ALWAYS** use `DefaultTestSteps` for player/session setup.
- **NEVER** modify the `multi-source-test-client` module.
- Always inherit from `BaseTest` or `BaseParameterizedTest`.
- Apply Allure annotations properly (`@Suite`, `@Epic`, `@Feature`, `@Story`).
- Follow the established assertion naming pattern for clarity.

## Common Patterns & Examples
- Review existing tests in `src/test/java` for idiomatic patterns. The casino-loss suite covers the full cross-system flow, while `FullRegistrationDebugTest` is ideal for smoke-checking registration defaults.
- Typical scenarios include: balance adjustment flows, bet/win/rollback sequences, and KYC verification chains.
- Use helper builders for request payloads instead of constructing JSON manually.

## DTO Objects (Field Map & Usage)
- **Response DTO rule:** Whenever you introduce a new HTTP response wrapper, declare it as a Java `record` (e.g., `public record LaunchGameResponseBody(...) {}`). Records are already used across the suite to guarantee immutability and concise field exposure, and Jackson handles them out of the box in our test runtime.
- **`RegisteredPlayerData`:** Thin wrapper that stores the *raw* HTTP authorization response together with the Redis snapshot of the wallet at registration time. Use it to avoid repeatedly hitting the APIs in every verification step.
  - `authorizationResponse` — `ResponseEntity<TokenCheckResponse>` produced by the FAPI `/token/check` call inside `DefaultTestSteps`. Access the bearer token via `registeredPlayer.getAuthorizationResponse().getBody().getToken()`; the helper already prefixes it with `Bearer`.
  - `walletData` — `WalletFullData` deserialized from Redis right after the player is created. It exposes the wallet UUID, player UUID, balance figures, limit collections, gambling map (keyed by transaction UUID), and helper records for bonus, deposits, and blocked amounts.
  - **Typical usage:**
    ```java
    String playerToken = ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken();
    String walletUuid = ctx.registeredPlayer.getWalletData().walletUUID();

    assertFalse(ctx.registeredPlayer.getWalletData().isBlocked(), "redis.wallet.is_blocked");
    ```
    For deep limit assertions rely on `WalletFullData#limits()` – each `LimitData` exposes amount/spent/rest fields already converted to `BigDecimal`.
- **`GameLaunchData`:** Couples the database entity returned by `WalletDatabaseClient` with the HTTP launch response, allowing the test to assert both persistence and the client-facing payload.
  - `dbGameSession` — `WalletGameSession` JPA entity fetched from the `game_session` table. Fields such as `gameSessionUuid`, `walletUuid`, `providerUuid`, and `gameCurrency` are available for DB-level checks.
  - `launchGameResponse` — `ResponseEntity<LaunchGameResponseBody>` from the game launch HTTP call. Extract the lobby URL via `getBody().getUrl()` and compare with `WalletGameSession#getGameExternalUuid()` or provider metadata when needed.
  - **Typical usage:**
    ```java
    assertEquals(ctx.gameLaunchData.getDbGameSession().getWalletUuid(), ctx.registeredPlayer.getWalletData().walletUUID(),
            "db.game_session.wallet_uuid");
    assertTrue(ctx.gameLaunchData.getLaunchGameResponse().getBody().getUrl().contains("session="),
            "fapi.launch.response.url_contains_session");
    ```
    Because both responses are cached inside the DTO, you can pass `GameLaunchData` between steps without re-querying Redis or the DB.
- **`GamblingResponseBody`:** Immutable record returned by every Manager gambling endpoint (`bet`, `win`, `refund`, `rollback`, `tournament`).
  - `balance` — wallet balance after the operation. The service already rounds it to the configured precision, so assert via `compareTo`.
  - `transactionId` — idempotency UUID echoed from the request. Use it to pair Manager responses with NATS/Kafka projections.
  - **Typical usage:**
    ```java
    var response = managerClient.win(casinoId, signature, request);
    assertAll("manager_api.win.body",
            () -> assertEquals(request.getTransactionId(), response.getBody().transactionId(), "manager_api.win.transactionId"),
            () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(response.getBody().balance()), "manager_api.win.balance"));
    ```
    Persist the `transactionId()` into the test context whenever downstream verifications need to reference the same UUID.

