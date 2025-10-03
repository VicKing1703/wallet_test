# kafka-api Module

Универсальный тестовый модуль для интеграции с внешними системами: **Kafka**, **NATS**, **Redis** и **HTTP API**.
Все клиенты используют единый fluent DSL, централизованную конфигурацию через JSON и автоматическую генерацию Allure-аттачей.

## Оглавление

- [HTTP Test Client](#http-test-client)
  - [Архитектура](#архитектура)
  - [Подключение и конфигурация](#подключение-и-конфигурация)
  - [Использование](#использование)
  - [Интеграция с Allure](#интеграция-с-allure)
- [Kafka Test Client](#kafka-test-client)
  - [Архитектура](#архитектура-1)
  - [Подключение и конфигурация](#подключение-и-конфигурация-1)
    - [DTO и сопоставление топиков](#dto-и-сопоставление-топиков)
    - [Зависимость Gradle](#зависимость-gradle)
    - [Spring-конфигурация реестра топиков](#spring-конфигурация-реестра-топиков)
    - [Настройки приложения](#настройки-приложения)
  - [Сценарии использования](#сценарии-использования)
    - [Методы fluent API](#методы-fluent-api)
    - [Комплексный пример](#комплексный-пример)
  - [Интеграция с Allure](#интеграция-с-allure-1)
- [NATS Test Client](#nats-test-client)
  - [Архитектура и ключевые компоненты](#архитектура-и-ключевые-компоненты)
  - [Подключение и конфигурация](#подключение-и-конфигурация-2)
    - [Зависимость Gradle](#зависимость-gradle-1)
    - [Файл окружения](#файл-окружения)
    - [Настройки клиента](#настройки-клиента)
  - [Сценарии использования](#сценарии-использования-1)
    - [Методы fluent API](#методы-fluent-api-1)
    - [Комплексный пример](#комплексный-пример-1)
  - [Интеграция с Allure](#интеграция-с-allure-2)
- [Redis Test Client](#redis-test-client)
  - [Архитектура и ключевые компоненты](#архитектура-и-ключевые-компоненты-1)
  - [Подключение и конфигурация](#подключение-и-конфигурации-3)
    - [Зависимость Gradle](#зависимость-gradle-2)
    - [Spring-конфигурация типов](#spring-конфигурация-типов)
    - [Настройки клиента](#настройки-клиента-1)
  - [Сценарии использования](#сценарии-использования-2)
    - [Методы fluent API](#методы-fluent-api-2)
    - [Комплексный пример](#комплексный-пример-2)
  - [Интеграция с Allure](#интеграция-с-allure-3)
- [Материалы для визуализаций](#материалы-для-визуализаций)

---

## HTTP Test Client

HTTP-клиент из модуля `kafka-api` построен на базе **Spring Cloud OpenFeign** и **OkHttp**. Конфигурация полностью автоматическая:
достаточно добавить сервис в JSON, и он сразу доступен для использования.

### Архитектура

- **OpenFeign** — декларативный HTTP клиент через аннотации
- **OkHttp** — транспорт с connection pooling (10 соединений, 5 минут keep-alive)
- **DynamicPropertiesConfigurator** — автоматически регистрирует все сервисы из JSON как Spring properties
- **Timeouts**: connect 10s, read/write 60s (переопределяется через `requestTimeoutMs`)
- **Retry on connection failure** — включен

---

### Подключение и конфигурация

#### Зависимость Gradle

```gradle
dependencies {
    testImplementation project(":kafka-api")
}
```

#### Настройки приложения

Фрагмент `configs/<env>.json`:

```json
{
  "http": {
    "defaults": {
      "baseUrl": "https://beta-09.b2bdev.pro",
      "concurrency": {
        "requestTimeoutMs": 5000,
        "defaultRequestCount": 2
      }
    },
    "services": {
      "fapi": {
        "baseUrl": "https://beta-09.b2bdev.pro"
      },
      "cap": {
        "baseUrl": "https://cap.beta-09.b2bdev.pro"
      },
      "payment": {
        "baseUrl": "https://payment.beta-09.b2bdev.pro",
        "timeout": 10000
      }
    }
  }
}
```

**Автоматическая регистрация:** любой сервис из `http.services.*` становится доступен как `${app.api.<service-name>.*}`

Примеры:
```
http.services.fapi    → ${app.api.fapi.base-url}
http.services.cap     → ${app.api.cap.base-url}
http.services.payment → ${app.api.payment.base-url}
                      → ${app.api.payment.timeout}
```

Дополнительные свойства (кроме `baseUrl`) автоматически конвертируются из camelCase в kebab-case.

---

### Использование

#### Создание Feign клиента

```java
@FeignClient(name = "fapiClient", url = "${app.api.fapi.base-url}")
public interface FapiClient {

    @GetMapping("/api/v1/wallets/{playerId}")
    WalletResponse getWallet(@PathVariable("playerId") String playerId);

    @PostMapping("/api/v1/transactions")
    TransactionResponse createTransaction(@RequestBody TransactionRequest request);
}
```

#### Активация клиентов

```java
@Configuration
@EnableFeignClients(basePackages = "com.example.tests.api.http.clients")
public class TestApiConfig {
}
```

#### Использование в тестах

```java
@SpringBootTest
public class WalletApiTest {

    @Autowired
    private FapiClient fapiClient;

    @Test
    void shouldGetWallet() {
        step("HTTP: Получение кошелька", () -> {
            WalletResponse wallet = fapiClient.getWallet(playerId);
            assertThat(wallet.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
    }
}
```

---

### Интеграция с Allure

Feign Logger автоматически логирует запросы и ответы с уровнем `FULL`: метод, URL, заголовки, тело, статус и время выполнения.

---

## Kafka Test Client

Kafka-клиент из модуля `kafka-api` помогает автотестам находить события в нужных топиках, ждать их появления и
прикладывать подробные артефакты в Allure. Он работает асинхронно, потокобезопасен и интегрируется со Spring Boot
через стандартные бины конфигурации.

### Архитектура

Высокоуровневый обмен между тестом, клиентом и Kafka выглядит так:

![Диаграмма взаимодействия Kafka-клиента](src/main/resources/docs/images/kafka-architecture-diagram.jpg)

---

## Подключение и конфигурация

Чтобы подключить Kafka-клиент в тестовом проекте:

1. Добавьте модуль `kafka-api` в тестовые зависимости Gradle.
2. Опишите DTO для сообщений и зарегистрируйте сопоставление DTO → топик в `KafkaTopicMappingRegistry`.
3. Настройте параметры Kafka в окруженческих JSON-файлах.

### DTO и сопоставление топиков

Создайте `record`, который отражает схему сообщения и будет десериализовываться клиентом:

```java
package com.uplatform.wallet_tests.api.kafka.dto;

public record BonusAwardMessage(
        String playerId,
        String bonusId,
        String status,
        long sequence
) {}
```

Затем зарегистрируйте DTO в `KafkaTopicMappingRegistry`, чтобы фоновые listener'ы подписались на нужный топик (см.
пример ниже).

### Зависимость Gradle

Добавьте модуль в тестовые зависимости:

```gradle
dependencies {
    testImplementation project(":kafka-api")
}
```

### Spring-конфигурация реестра топиков

Создайте бин `KafkaTopicMappingRegistry`, который сопоставляет DTO и суффиксы топиков:

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public KafkaTopicMappingRegistry kafkaTopicMappingRegistry() {
        Map<Class<?>, String> mappings = new HashMap<>();
        mappings.put(BonusAwardMessage.class, "bonus.v1.award");
        // другие сопоставления
        return new SimpleKafkaTopicMappingRegistry(mappings);
    }
}
```

Реестр гарантирует, что фоновые listener'ы подпишутся на каждый указанный топик автоматически — ничего дополнительно
в `application.yml` прописывать не нужно.

### Настройки приложения

Фрагмент `configs/local.json`, который покрывает основные параметры клиента:

```json
{
  "kafka": {
    "bootstrapServer": "kafka-development-01:9092,kafka-development-02:9092,kafka-development-03:9092",
    "groupId": "wallet-tests-consumer",
    "bufferSize": 500,
    "findMessageTimeout": "PT60S",
    "findMessageSleepInterval": "PT0.2S",
    "pollDuration": "PT1S",
    "shutdownTimeout": "PT5S",
    "autoOffsetReset": "latest",
    "enableAutoCommit": true
  }
}
```

Все параметры читаются через `KafkaProperties` и автоматически прокидываются в `KafkaClient` через Spring. Изменяйте их
в JSON-конфигах окружений, чтобы не править код при переключении между стендами.

- `topicPrefix` задаётся в `EnvironmentConfig.topicPrefix`. Добавляется ко всем Kafka-топикам, например `wallet.beta.`.
- `bootstrapServer` в блоке `kafka.bootstrapServer` перечисляет брокеры, к которым подключается consumer: `kafka-dev-01:9092,kafka-dev-02:9092`.
- `groupId` (`kafka.groupId`) — идентификатор consumer group для всех автотестов, например `wallet-tests-consumer`.
- `bufferSize` (`kafka.bufferSize`) ограничивает количество сообщений на топик в буфере. Типичное значение — `500`.
- `findMessageTimeout` (`kafka.findMessageTimeout`) задаёт таймаут ожидания для `fetch()` по умолчанию, например `PT20S`.
- `findMessageSleepInterval` (`kafka.findMessageSleepInterval`) определяет паузу между попытками поиска сообщения, например `PT0.2S`.
- `pollDuration` (`kafka.pollDuration`) описывает максимальную блокировку вызова `poll` у consumer'а, обычно `PT1S`.
- `shutdownTimeout` (`kafka.shutdownTimeout`) — время на корректное завершение consumer при остановке тестов, например `PT5S`.
- `autoOffsetReset` (`kafka.autoOffsetReset`) регулирует поведение при отсутствии offset'ов (`latest` или `earliest`).
- `enableAutoCommit` (`kafka.enableAutoCommit`) включает автоматический коммит offset'ов, чаще всего `true`.

## Сценарии использования

### Методы fluent API

- `kafkaClient.expect(Class<T>)` — вызывается на бине `KafkaClient`, стартует построение ожидания для указанного DTO и
  подхватывает таймаут по умолчанию из конфигурации.
- `.with(String key, Object value)` — вызывается на билдере, добавляет JsonPath-фильтр. Значение сериализуется в `String`,
  `null` и пустые строки игнорируются, вложенные поля описываются как `data.player.id` или `$.path.to.field`.
- `.unique()` — включает проверку уникальности события в буфере. При нарушении будет выброшено
  `KafkaMessageNotUniqueException`, при отсутствии подходящего сообщения — `KafkaMessageNotFoundException`.
- `.within(Duration timeout)` — переопределяет таймаут ожидания только для текущего запроса. Подходит для ускоренных или
  долгих сценариев без изменения глобальной настройки.
- `.fetch()` — выполняет поиск сообщения и возвращает десериализованный DTO, формируя Allure-аттачи даже при таймауте или
  ошибке десериализации.

Комбинируйте методы цепочкой, чтобы описать нужное ожидание. Фильтры применяются одновременно, поэтому событие должно
удовлетворять всем заданным условиям.

### Комплексный пример

```java
step("Kafka: Получение сообщения из топика limits.v2", () -> {
    var expectedAmount = initialAmount.stripTrailingZeros().toPlainString();
    LimitMessage kafkaLimitMessage = kafkaClient.expect(LimitMessage.class)
            .with("playerId", registeredPlayer.getWalletData().playerUUID())
            .with("limitType", NatsLimitType.SINGLE_BET.getValue())
            .with("currencyCode", registeredPlayer.getWalletData().currency())
            .with("amount", expectedAmount)
            .fetch();

    assertNotNull(kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
});
```

Комбинируйте `.unique()` и `within(...)`, чтобы гибко управлять проверками и таймаутами.

---

## Интеграция с Allure

При каждом поиске клиент формирует аттачи:

- **Search Info** — условия поиска (топик, DTO, фильтры, таймаут).
- **Found Message** — форматированное тело найденного события, partition, offset и timestamp.
- **Message Not Found** — отчёт о таймауте и несостоявшемся поиске.
- **Deserialization Error** — подробности ошибки Jackson и исходный payload.

Пример того, как это выглядит в Allure:
![Allure-отчёт с Kafka-аттачами](src/main/resources/docs/images/allure-report-example.jpg)
---

## NATS Test Client

Клиент NATS в модуле `kafka-api` работает поверх JetStream, повторяя знакомый по Kafka и Redis подход: ожидания описываются через
fluent API, параметры читаются из единого JSON-конфига, а результат сопровождается аттачами в Allure.

### Архитектура и ключевые компоненты

- **`NatsClient`.** Фасад, который собирает ожидание (`expect(...)`) и делегирует поиск в `NatsSubscriber`.
- **`NatsSubscriber`.** Управляет JetStream-подпиской в отдельном dispatcher'е, применяет фильтры и следит за таймаутами.
- **`NatsPayloadMatcher`.** Сравнивает payload по JSONPath-выражениям и метаданным сообщения.
- **`NatsAttachmentHelper`.** Формирует единый набор аттачей: Search Info, Found Message, Duplicate Message и Message Not Found.
- **`NatsConnectionManager`.** Настраивает подключение и кэширует `Connection`/`JetStream` на время тестового прогона.

Такое разделение повторяет паттерн Kafka-клиента, поэтому интеграция с тестами и Spring остаётся однородной.

### Подключение и конфигурация

#### Зависимость Gradle

```gradle
dependencies {
    testImplementation project(":kafka-api")
}
```

#### Файл окружения

`EnvironmentConfigurationProvider` читает JSON `configs/<env>.json` (значение передаётся через `-Denv=...`) и прокидывает блок
`nats` в `NatsConfigProvider`:

```json
{
  "name": "beta",
  "natsStreamPrefix": "beta_",
  "nats": {
    "hosts": ["nats://nats-1:4222", "nats://nats-2:4222"],
    "streamName": "wallet-events",
    "subscriptionRetryCount": 3,
    "subscriptionRetryDelayMs": 500,
    "connectReconnectWaitSeconds": 2,
    "connectMaxReconnects": 10,
    "searchTimeoutSeconds": 30,
    "subscriptionAckWaitSeconds": 5,
    "subscriptionInactiveThresholdSeconds": 60,
    "subscriptionBufferSize": 256,
    "uniqueDuplicateWindowMs": 400,
    "failOnDeserialization": true
  }
}
```

Полное имя стрима формируется как `natsStreamPrefix + streamName`; хелпер `buildWalletSubject(...)` дополнительно добавляет
окруженческий префикс и wildcard-сегмент для канала.

#### Настройки клиента

- `hosts` (`nats.hosts`) — список URL JetStream-кластера, к которым подключается клиент.
- `streamName` (`nats.streamName`) — базовое имя стрима без окруженческого префикса; окружение добавляется через `natsStreamPrefix`.
- `subscriptionRetryCount` / `subscriptionRetryDelayMs` (`nats.subscriptionRetryCount` / `nats.subscriptionRetryDelayMs`) — количество попыток создания подписки и пауза между ними.
- `connectReconnectWaitSeconds` / `connectMaxReconnects` (`nats.connectReconnectWaitSeconds` / `nats.connectMaxReconnects`) — стратегия переподключений при потере соединения.
- `searchTimeoutSeconds` (`nats.searchTimeoutSeconds`) — таймаут ожидания сообщения по умолчанию; метод `.within(...)` переопределяет его локально.
- `subscriptionAckWaitSeconds` / `subscriptionInactiveThresholdSeconds` (`nats.subscriptionAckWaitSeconds` / `nats.subscriptionInactiveThresholdSeconds`) — таймауты ack и неактивности JetStream-подписки.
- `subscriptionBufferSize` (`nats.subscriptionBufferSize`) — размер буфера сообщений на подписку в памяти клиента.
- `uniqueDuplicateWindowMs` (`nats.uniqueDuplicateWindowMs`) — окно, в пределах которого работает контроль дублей для `.unique()`.
- `failOnDeserialization` (`nats.failOnDeserialization`) — при значении `true` ожидание завершается ошибкой, если payload нельзя десериализовать.

## Сценарии использования

### Методы fluent API

NATS-DSL полностью перешёл на JSONPath- и metadata-фильтры, повторяя подход Kafka-клиента и делая поиск сообщений детерминированным.

- `natsClient.expect(Class<T>)` — стартует построение ожидания для DTO и подхватывает таймаут по умолчанию из конфигурации.
- `.from(String subject)` — задаёт subject JetStream, с которого нужно читать события. Хелпер `buildWalletSubject(...)` помогает формировать его из идентификаторов игрока/кошелька.
- `.with(String jsonPath, Object value)` — добавляет JSONPath-фильтр к payload. Значение сериализуется в строку; `null` и пустые строки игнорируются. Можно комбинировать выражения любой вложенности: `$.playerId`, `$.data.limit.amount`, `data.balance`.
- `.withType(String type)` — проверяет metadata-поле `type`, удобно для разграничения событий одного DTO.
- `.withSequence(long sequence)` — фиксирует ожидаемый порядковый номер сообщения в стриме.
- `.unique()` — включает контроль дублей, используя окно `uniqueDuplicateWindowMs` из конфигурации.
- `.unique(Duration window)` — задаёт собственное окно для поиска дублей; повторяющиеся сообщения приводят к `NatsDuplicateMessageException` и отдельному аттачу.
- `.within(Duration timeout)` — переопределяет таймаут ожидания только для текущего запроса.
- `.fetch()` — выполняет поиск сообщения, возвращает `NatsMessage<T>` и формирует аттачи даже при таймауте или ошибке.

Все фильтры применяются одновременно, поэтому сообщение должно удовлетворять каждому условию сразу.

### Комплексный пример

```java
step("NATS: Получаем событие о создании лимита", () -> {
    String subject = natsClient.buildWalletSubject(playerId.toString(), walletId.toString());

    NatsMessage<WalletLimitEvent> message = natsClient.expect(WalletLimitEvent.class)
            .from(subject)
            .with("$.playerId", playerId.toString())
            .with("$.limitType", expectedLimitType)
            .withType("LimitCreatedEvent")
            .withSequence(expectedSequence)
            .unique(Duration.ofSeconds(5))
            .within(Duration.ofSeconds(30))
            .fetch();

    assertThat(message.getPayload().limitType()).isEqualTo(expectedLimitType);
});
```

Комбинируйте `.unique()` и `within(...)`, чтобы гибко управлять таймаутами и проверкой дублей.

### Интеграция с Allure

Каждый поиск формирует единый набор вложений:

- **Search Info** — subject, таймаут, тип DTO и полный список JSONPath/metadata-фильтров.
- **Found Message** — форматированный payload с метаданными JetStream.
- **Duplicate Message** — появляется при нарушении `.unique()`.
- **Message Not Found** — содержит информацию о таймауте и применённых фильтрах.

---

## Redis Test Client

Redis-клиент из `kafka-api` повторяет знакомую структуру Kafka- и NATS-модулей: fluent DSL описывает ожидание значения по ключу,
конфигурация хранится во внешних JSON/YAML файлах, а результат сопровождён информативными аттачами в Allure.

### Архитектура и ключевые компоненты

- **`GenericRedisClient`.** Точечный бин для каждого инстанса, разворачиваемого из конфигурации. Инициализирует билдер ожидания через
  `.key(...)` и прокидывает типы/зависимости.
- **`RedisExpectationBuilder`.** Fluent API, который применяет JsonPath-фильтры, управляет таймаутом и формирует отчёт об успешном
  чтении или таймауте.
- **`RedisTypeMappingRegistry`.** Реестр `TypeReference`, связывающий логические имена клиентов (`wallet`, `player`) с DTO для
  десериализации ответа.
- **`RedisAwaitilityProperties`.** Настройки повторных попыток (`retryAttempts`, `retryDelay`) и таймаутов, используемые по
  умолчанию билдером.

### Подключение и конфигурация

#### Зависимость Gradle

```gradle
dependencies {
    testImplementation project(":kafka-api")
}
```

#### Spring-конфигурация типов

Опишите бин `RedisTypeMappingRegistry`, чтобы модули тестов получили типобезопасный доступ к значениям:

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTypeMappingRegistry redisTypeMappingRegistry() {
        return new RedisTypeMappingRegistry()
                .register("wallet", new TypeReference<WalletFullData>() {})
                .register("player", new TypeReference<Map<String, WalletData>>() {});
    }
}
```

Каждый блок `clients.<name>` из конфигурации разворачивается в бин `redis<Имя>Client`, поэтому `clients.wallet` создаёт
`redisWalletClient`, а `clients.player` — `redisPlayerClient`.

#### Настройки клиента

Блок `redis` в `application.yml` или `configs/<env>.json` управляет ретраями, подключениями и пулами Lettuce:

```yaml
redis:
  aggregate:
    maxGamblingCount: 50
    maxIframeCount: 500
    retryAttempts: 20
    retryDelayMs: 500
  clients:
    wallet:
      host: redis-01.b2bdev.pro
      port: 6390
      database: 9
      timeout: 5000ms
      password: secret # опционально
      lettucePool:
        maxActive: 8
        maxIdle: 8
        minIdle: 0
        maxWait: 2s
        shutdownTimeout: 100ms
    player:
      host: redis-01.b2bdev.pro
      port: 6389
      database: 9
      timeout: 5s
```

- `aggregate.retryAttempts` / `aggregate.retryDelayMs` — количество повторов чтения и задержка между ними для Awaitility.
- `aggregate.maxGamblingCount` / `aggregate.maxIframeCount` — границы агрегатов, которые используют вспомогательные хелперы тестов.
- `clients.<name>` — описание Redis-инстанса и имя создаваемого бина `redis<Имя>Client`.
- `timeout` — таймаут операций Lettuce; поддерживаются ISO 8601 (`PT5S`) и значения с суффиксами (`5000ms`).
- `password` — опциональный пароль для подключения.
- `lettucePool.*` — параметры пула: лимиты активных/свободных подключений, ожидание и таймаут завершения.

### Сценарии использования

#### Методы fluent API

- `redis<Имя>Client.key(String key)` — стартует ожидание для конкретного ключа, проверяя, что он не пустой.
- `.with(String jsonPath, Object value)` — сравнивает значение JsonPath с ожидаемым, корректно обрабатывая числа и строки.
- `.with(String jsonPath, Predicate<Object> predicate)` / `.with(..., String description)` — позволяет описать произвольное условие и
  человекочитаемое описание, которое попадёт в аттач.
- `.withAtLeast(String jsonPath, Number threshold)` — проверяет, что числовое значение не меньше порога.
- `.within(Duration timeout)` — переопределяет таймаут только для текущего запроса; иначе используется значение из
  `RedisAwaitilityProperties`.
- `.fetch()` — выполняет чтение с ретраями, возвращает десериализованный DTO и формирует аттачи для Allure.

Фильтры применяются одновременно: значение должно удовлетворять каждому JsonPath-условию, чтобы запрос завершился успехом.

#### Комплексный пример

```java
step("Redis: Проверяем агрегат кошелька и связанный кеш", () -> {
    WalletFullData aggregate = redisWalletClient.key("wallet:aggregate:" + playerId)
            .with("$.playerId", playerId)
            .with("$.metadata.environment", environmentCode)
            .withAtLeast("$.balances.main", BigDecimal.ZERO)
            .with("$.limits", value -> !((Collection<?>) value).isEmpty(), "contains at least one limit")
            .within(Duration.ofSeconds(15))
            .fetch();

    Map<String, WalletData> wallets = redisPlayerClient.key("player:wallets:" + playerId)
            .with("$.entries['" + playerId + "'].currency", aggregate.balances().currency())
            .fetch();

    assertNotNull(wallets.get(playerId), "then.redis.player.wallet_present");
});
```

Таймаут и фильтры можно комбинировать в любой последовательности. При истечении времени будет выброшено
`RedisRetryExhaustedException` с подробными причинами последней попытки.

### Интеграция с Allure

Каждый вызов `fetch()` формирует стандартные вложения:

- **Search Info** — имя бина, ключ, таймаут и полный список JsonPath-фильтров.
- **Found Value** — форматированный JSON найденного значения.
- **Value Not Found** — итоговая причина таймаута и фактическое время ожидания.
- **Deserialization Error** — исходный JSON и сообщение Jackson, если десериализация провалилась.

Эти аттачи упрощают анализ Redis-проверок и полностью соответствуют стилю Kafka/NATS клиентов.

---

## Материалы для визуализаций

- `docs/images/kafka-architecture-diagram.png` — путь для PNG/WEBP диаграммы, экспортированной из Mermaid-сценария выше.
- `docs/images/allure-report-example.png` — путь для скриншота отчёта Allure с аттачами Kafka.

Храните файлы в каталоге `docs/images` в корне репозитория и подключайте их из README через относительный путь
`../docs/images/<filename>`.
