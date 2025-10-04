# 📖 README
# Наш подход к автоматизации тестирования

Этот фреймворк помогает нам писать интеграционные тесты без лишней рутины. Он построен на **Spring Boot** и читает параметры окружения из единых JSON-файлов.
Достаточно запустить тесты с `-Denv=<имя>` — и все клиенты для API, баз данных, Redis, Kafka и NATS автоматически подключатся к нужным сервисам.

Клиенты не хранят состояние и потокобезопасны, поэтому тесты можно смело запускать параллельно. Благодаря DI тестовые классы остаются лаконичными и хорошо читаемыми.
* **Единая конфигурация окружения.** `DynamicPropertiesConfigurator` загружает параметры
  из json и передаёт их в Spring, поэтому тесты легко переключать между окружениями
  без правки кода.
* **Набор специализированных клиентов.** Для HTTP, Kafka, NATS, Redis и MySQL
  предусмотрены собственные классы с ожиданиями, фильтрами и аттачами в Allure.
* **Расширенное логирование и отчёты.** HTTP‑запросы и ответы логируются
  `AllureFeignLogger`, события из Kafka и NATS автоматически прикладываются к отчёту,
  а операции с Redis и БД снабжены подробными логами.
* **Единый формат аттачей.** Все клиенты используют `AllureAttachmentService`
  совместно с перечислением `AttachmentType`, что позволяет получать однородные
  заголовки аттачей во всех разделах отчёта.
* **Поддержка асинхронных сценариев.** Клиенты Kafka и NATS умеют ждать события
  с таймаутами и ретраями, что облегчает тестирование сложных бизнес‑процессов.
* **Гибкая автоконфигурация.** Фреймворк легко расширять: достаточно описать
  новый клиент и указать параметры в конфигурации.
* **Высокая скорость и потокобезопасность.** Клиенты не хранят состояние и могут работать параллельно, что ускоряет выполнение тестов.

Мы внедряем этот фреймворк, чтобы выпускать новый функционал сразу с автотестами и не накапливать технический долг. Ранее мы вели рукописные тест-кейсы, а теперь полагаемся на самодокументируемый код и отчёты Allure.

Такой подход позволяет строить **масштабируемую и понятную** тестовую базу,
способную проверять распределённые сервисы с множеством точек интеграции.

---

## Оглавление

*   [🌐 Работа с HTTP](#работа-с-http)
    *   [1. Как устроена работа с HTTP](#1-как-устроена-работа-с-http)
    *   [2. Как настроить подключение](#2-как-настроить-подключение)
    *   [3. Где прописать базовый-url](#3-где-прописать-базовый-url)
    *   [4. Как описать эндпоинт](#4-как-описать-эндпоинт)
    *   [5. Пример DTO](#5-пример-dto)
    *   [6. Пример клиента](#6-пример-клиента)
    *   [7. Использование в тестах](#7-использование-в-тестах)
*   [⚙️ Работа с Kafka](#работа-с-kafka)
    *   [1. Как устроена работа с Kafka](#1-как-устроена-работа-с-kafka)
    *   [2. Как настроить подключение](#2-как-настроить-подключение-1)
    *   [3. Где прописать адрес брокера](#3-где-прописать-адрес-брокера)
    *   [4. Как работает абстрактный класс](#4-как-работает-абстрактный-класс-1)
    *   [5. Подключение нового топика](#5-подключение-нового-топика)
    *   [6. Пример клиента для нового топика](#6-пример-клиента-для-нового-топика)
    *   [7. Использование в тестах](#7-использование-в-тестах-1)
*   [⚡ Работа с Redis](#работа-с-redis)
    *   [1. Как устроена работа с Redis](#1-как-устроена-работа-с-redis)
    *   [2. Как настроить подключение](#2-как-настроить-подключение-2)
    *   [3. Где прописать адрес сервера](#3-где-прописать-адрес-сервера)
    *   [4. Как работает абстрактный класс](#4-как-работает-абстрактный-класс-2)
    *   [5. Подключение нового инстанса](#5-подключение-нового-инстанса)
    *   [6. Пример клиента для нового инстанса](#6-пример-клиента-для-нового-инстанса)
    *   [7. Использование в тестах](#7-использование-в-тестах-2)
*   [🗄️ Работа с БД](#работа-с-бд)
    *   [1. Как устроена работа с БД](#1-как-устроена-работа-с-бд)
    *   [2. Как настроить подключение](#2-как-настроить-подключение-3)
    *   [3. Где прописать адрес сервера](#3-где-прописать-адрес-сервера-1)
    *   [4. Как работает абстрактный класс](#4-как-работает-абстрактный-класс-3)
    *   [5. Подключение новой базы](#5-подключение-новой-базы)
    *   [6. Пример клиента](#6-пример-клиента-1)
    *   [7. Использование в тестах](#7-использование-в-тестах-3)
*   [📨 Работа с NATS](#работа-с-nats)
    *   [1. Как устроена работа с NATS](#1-как-устроена-работа-с-nats)
    *   [2. Как настроить подключение](#2-как-настроить-подключение-4)
    *   [3. Где прописать адрес сервера](#3-где-прописать-адрес-сервера-2)
    *   [4. Как работает клиент](#4-как-работает-клиент)
    *   [5. Подключение нового subject](#5-подключение-нового-subject)
    *   [6. Пример поиска сообщения](#6-пример-поиска-сообщения)
    *   [7. Использование в тестах](#7-использование-в-тестах-4)
*   [🔗 Тестовый контекст](#тестовый-контекст-и-передача-данных)
*   [🧪 Сквозной пример](#сквозной-пример)

*(По мере добавления новых секций, не забудьте обновить оглавление)*

---


## Работа с HTTP

### 1. Как устроена работа с HTTP

В основе лежат Feign-клиенты, описанные интерфейсами с аннотациями Spring MVC.
Все такие интерфейсы собираются в бины через автоконфигурацию и доступны в
контексте тестов. За логирование запросов и ответов отвечает автоконфигурация
`HttpApiAutoConfiguration`, поэтому каждая HTTP‑операция автоматически
добавляется в отчёт Allure вместе с телом и заголовками.

### 2. Как настроить подключение

Перед запуском тестов укажите системное свойство `-Denv=<имя_окружения>`.
Нужный файл конфигурации находится в каталоге `configs`. В разделе `http`
описываются дефолтные параметры и набор сервисов с адресами. Чувствительные
значения, такие как секреты и логины, вынесены в секцию `walletTests.http`.
Расширение тестового модуля реализует `EnvironmentConfigPostProcessor` и
накладывает эти значения поверх базовой конфигурации уже после того, как
`kafka-api` загрузил окружение. Класс `DynamicPropertiesConfigurator`
читает объединённый набор значений,
публикует их в пространстве `app.http.*` и оставляет прежние алиасы
`app.api.*` для обратной совместимости. Параметры платформы аналогично
располагаются в `walletTests.platform` и добавляются тем же постпроцессором,
после чего становятся доступны через `EnvironmentConfig#getPlatform()`.

### 3. Где прописать базовый-url

В файле окружения присутствует параметр `http.services.manager.base-url`:

```json
"http": {
  "services": {
    "manager": {
      "baseUrl": "https://manager.test.host"
    }
  }
}
```

### 4. Как описать эндпоинт

Создайте интерфейс и пометьте его `@FeignClient`. В параметре `url` используйте
переменную `${app.api.manager.base-url}` так, чтобы один клиент можно было
подключать к разным окружениям. Методы описывайте стандартными аннотациями
Spring MVC (`@GetMapping`, `@PostMapping` и др.).

```java
@FeignClient(name = "managerClient", url = "${app.api.manager.base-url}")
public interface ManagerClient {
    @PostMapping("/_core_gas_processing/bet")
    ResponseEntity<GamblingResponseBody> bet(
            @RequestHeader("X-Casino-Id") String casinoId,
            @RequestHeader("Signature") String signature,
            @RequestBody BetRequestBody request
    );
}
```

### 5. Пример DTO
Классы запросов оформляйте при помощи Lombok, а ответы описывайте как `record`
— так мы гарантируем неизменяемость и прозрачные геттеры вида `field()`.

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetRequestBody {
    private String     sessionToken;
    private BigDecimal amount;
    private String     transactionId;
    private String     type;
    private String     roundId;
    private Boolean    roundClosed;
}

/**
 * Ответ Manager API после ставок/выигрышей/возвратов.
 * @param balance Баланс игрока после операции.
 * @param transactionId Idempotency UUID, совпадает с переданным в запрос.
 */
public record GamblingResponseBody(BigDecimal balance, String transactionId) {
}
```

### 6. Пример клиента
Инжектируйте клиент в тестовый класс через `@Autowired`.

```java
@Autowired
private ManagerClient managerClient;
```

### 7. Использование в тестах
Оформите запрос к API прямо в шаге `Allure.step` и сделайте проверки:

```java
@Autowired
private ManagerClient managerClient;

step("HTTP: отправка запроса Bet", () -> {
    BetRequestBody request = BetRequestBody.builder()
            .sessionToken(sessionToken)
            .amount(new BigDecimal("10.15"))
            .transactionId(transactionId)
            .type("bet")
            .roundId(roundId)
            .roundClosed(false)
            .build();

    ResponseEntity<GamblingResponseBody> response = managerClient.bet(
            casinoId,
            signature,
            request
    );

    // ctx — объект с подготовленными данными теста.
    BigDecimal expectedBalance = ctx.expectedBalanceAfterBet;
    assertAll(
            () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
            () -> assertEquals(transactionId, response.getBody().transactionId()),
            () -> assertEquals(0, expectedBalance.compareTo(response.getBody().balance()))
    );
});
```
## Работа с Kafka

### 1. Как устроена работа с Kafka

В тестовом фреймворке сообщения Kafka читаются фоновым сервисом.
Класс `KafkaBackgroundConsumer` запускает `KafkaPollingService`.
`KafkaPollingService` создаёт `KafkaMessageListenerContainer` для каждого указанного в конфигурации топика и подписывается на него.
Полученные записи помещаются в буфер `MessageBuffer`, представляющий собой кольцевую очередь.
Поиск по буферу и десериализацию выполняет `MessageFinder`, возвращая тесту DTO нужного типа.

#### Архитектура

* **KafkaClient** — публичный фасад. Создаёт билдер ожиданий и передаёт ему фонового consumer и таймауты.
* **KafkaExpectationBuilder** — fluent-конструктор. Сохраняет фильтры, таймаут, флаг уникальности и выполняет поиск с проверками.
* **KafkaBackgroundConsumer** — управляет подписками, выполняет асинхронный поиск в буфере и взаимодействует с Allure.
* **KafkaPollingService** — низкоуровневый сервис Spring Kafka, который подписывается на топики, сразу смещает consumer в конец и складывает записи в буфер.
* **MessageBuffer** — потокобезопасный буфер на каждый топик. Следит за переполнением и хранит ограниченное количество сообщений.
* **MessageFinder** — применяет JsonPath-фильтры, десериализует сообщения и возвращает совпадения, а также сообщает об ошибках десериализации.

### 2. Как настроить подключение

При запуске тестов укажите системное свойство `-Denv=<имя_окружения>`.
В каталоге `configs` хранятся json-файлы с настройками для разных окружений.
В разделе `kafka` задаются `bootstrapServer`, `groupId` и дополнительные параметры (тайм-ауты, размер пула и т.п.).

### 3. Где прописать адрес брокера

Адреса брокеров и набор прослушиваемых топиков задаются в том же файле конфигурации. Значения читаются классом `DynamicPropertiesConfigurator` и передаются в `KafkaConsumerConfig`. Ниже приведён фрагмент из `beta-09.json`:

```json
"kafka": {
  "bootstrapServer": "kafka-development-01.b2bdev.pro:9092,kafka-development-02.b2bdev.pro:9092,kafka-development-03.b2bdev.pro:9092",
  "groupId": "cb-wallet-test-consumer-beta-09"
}
```

### 4. Как работает клиент Kafka

`KafkaClient` наследует `AbstractKafkaClient` и предоставляет универсальные методы ожидания сообщений.
Топики настраиваются через `KafkaConsumerConfig`, где каждому классу DTO сопоставляется суффикс топика.
Клиент ищет сообщения в `MessageBuffer` и десериализует их в нужный тип.

Основной DSL:
- `expect(Class<T>)` — подготовить ожидание сообщения указанного типа.
  Фильтры добавляйте через `.with("key", value)`, уникальность — `.unique()`,
  таймаут — `.within(Duration)` и завершайте ожидание методом `.fetch()`.

### 5. Подключение нового топика

1. Создайте DTO в пакете `api/kafka/dto`.
2. Добавьте соответствие между DTO и суффиксом топика в `KafkaConsumerConfig`:

```java
@Bean
public KafkaTopicMappingRegistry kafkaTopicMappingRegistry() {
    Map<Class<?>, String> mappings = new HashMap<>();
    // существующие топики
    mappings.put(PlayerAccountMessage.class, "player.v1.account");
    mappings.put(WalletProjectionMessage.class, "wallet.v8.projectionSource");
    mappings.put(GameSessionStartMessage.class, "core.gambling.v1.GameSessionStart");
    mappings.put(LimitMessage.class, "limits.v2");
    // новый топик
    mappings.put(BonusAwardMessage.class, "bonus.v1.award");
    return new SimpleKafkaTopicMappingRegistry(mappings);
}
```
После перезапуска тестов `MessageBuffer` начнёт автоматически слушать указанный топик благодаря обновлённому реестру.

### 6. Использование в тестах

Инжектируйте `KafkaClient` и ожидайте сообщение внутри `Allure.step`:

```java
@Autowired
private KafkaClient kafkaClient;

step("Kafka: получение сообщения", () -> {
    var message = kafkaClient.expect(WalletProjectionMessage.class)
            .with("seq_number", testData.someEvent.getSequence())
            .fetch();
    assertTrue(utils.areEquivalent(message, testData.someEvent));
});
```

#### FAQ / Troubleshooting

**Вопрос:** тест падает с `KafkaMessageNotFoundException`, хотя сообщение точно отправлено. Что проверить?

* Убедитесь, что фильтры `.with(...)` совпадают с фактическим payload (особенно регистр и формат чисел).
* Проверьте, что суффикс топика для нужного DTO добавлен в `KafkaTopicMappingRegistry`.
* Убедитесь, что префикс топика (environment prefix) совпадает с фактическим именем топика.
* Увеличьте таймаут через `.within(...)` для дополнительной диагностики.
* Просмотрите логи на наличие ошибок подключения к Kafka или десериализации.

**Вопрос:** добавил новый DTO, но клиент не находит топик.

* Убедитесь, что в бине `KafkaTopicMappingRegistry` добавлен маппинг `mappings.put(MyNewMessage.class, "my.new.topic.suffix")`. Без этого клиент не сможет вычислить полное имя топика.

## Работа с Redis

### 1. Как устроена работа с Redis

Фреймворк автоматически регистрирует типизированные клиенты `GenericRedisClient<T>`
на основе конфигурации. Каждый клиент предоставляет fluent-API через
`RedisExpectationBuilder`, позволяющий декларативно описывать ожидания:

```java
var aggregate = redisWalletClient
        .key(ctx.registeredPlayer.getWalletData().walletUUID())
        .withAtLeast("LastSeqNumber", (int) ctx.betEvent.getSequence())
        .with("isGamblingActive", true)
        .within(Duration.ofSeconds(10))
        .fetch();
```

В процессе ожидания автоматически прикладываются Allure-аттачи: информация
о поиске, найденное значение либо причины таймаута, а также ошибки
десериализации. JSONPath-фильтры поддерживаются из коробки.

Вся инфраструктура (connection factory, `RedisTemplate`, клиенты и настройки Awaitility)
поднимается автоконфигурацией `RedisApiAutoConfiguration`. Она активируется только
если в окружении присутствует раздел `redis.clients`, поэтому проекты, которым
Redis не нужен, остаются нетронутыми.

### 2. Как настроить подключение

Запускайте тесты с системным свойством `-Denv=<имя_окружения>`. В каталоге
`configs` хранится конфигурация, где раздел `redis` содержит параметры повторов
и список клиентов. Отдельный `application.yml` больше не нужен — все значения
передаются из JSON через `DynamicPropertiesConfigurator`. Пример из `beta-09.json`:

```json
"redis": {
    "aggregate": {
        "maxGamblingCount": 50,
        "maxIframeCount": 500,
        "retryAttempts": 10,
        "retryDelayMs": 200
    },
    "clients": {
      "wallet": {
        "host": "redis-01.b2bdev.pro",
        "port": 6390,
        "database": 9,
        "timeout": "5000ms",
        "lettucePool": {
          "maxActive": 8,
          "maxIdle": 8,
          "minIdle": 0,
          "shutdownTimeout": "100ms"
        }
      },
      "player": {
        "host": "redis-01.b2bdev.pro",
        "port": 6389,
        "database": 9,
        "timeout": "5000ms",
        "lettucePool": {
          "maxActive": 8,
          "maxIdle": 8,
          "minIdle": 0,
          "shutdownTimeout": "100ms"
        }
      }
    }
}
```

### 3. Где прописать адрес сервера

Все данные подключения располагаются в этом же конфигурационном файле в разделе
`redis.clients`. `DynamicPropertiesConfigurator` переносит их в Spring Environment,
после чего автоконфигурация Redis автоматически создает для каждого клиента
подключение и `RedisTemplate`.

### 4. Как работает ожидание

`RedisExpectationBuilder` использует Awaitility для повторных попыток чтения
значения. Можно добавлять произвольное количество JSONPath-фильтров, переопределять
таймаут (`within(...)`) и комбинировать их с кастомными предикатами. Все попытки
сопровождаются информативными аттачами в Allure, поэтому в тесте достаточно
вызвать `fetch()` и проверить полученный DTO.

### 5. Минимальная конфигурация в тестовом проекте

1. Убедитесь, что в файле окружения заданы `redis.clients` с параметрами подключения
   для каждого нужного сервиса.

2. Создайте конфигурационный класс с единственным бином `RedisTypeMappingRegistry`:

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

   Ключи, переданные в `register(...)`, должны совпадать с именами клиентов из
   `redis.clients`.

На этом настройка завершена. Автоконфигурация сама создаст подключения, шаблоны
и `GenericRedisClient`-бины для каждого описанного клиента.

### 6. Пример настройки и использования

Инжекция клиента в тестовый класс выглядит привычно:

```java
@Autowired
private GenericRedisClient<WalletFullData> redisWalletClient;
```

Далее можно гибко описывать ожидания напрямую в шаге:

```java
step("Redis: проверяем агрегат кошелька", () -> {
    var aggregate = redisWalletClient
            .key(testData.walletUuid())
            .with("$.lastSeqNumber", testData.expectedSeq())
            .with("$.isGamblingActive", true)
            .within(Duration.ofSeconds(10))
            .fetch();

    assertThat(aggregate.getWallet().getBalance()).isEqualTo(testData.expectedBalance());
});
```

Для сложных случаев можно передавать дополнительные предикаты через
перегрузку `with(jsonPath, predicate)`, использовать вспомогательные матчеры либо полностью
положиться на JSONPath-фильтры. Все попытки чтения снабжаются Allure-аттачами,
поэтому при падениях в отчёте будет детально видно, что именно искали и что
оказалось в Redis.

## Работа с БД

### 1. Как устроена работа с БД

Доступ к MySQL осуществляют клиенты на базе Spring Data JPA. Для разных
источников данных объявлены отдельные `DataSource` и `EntityManager`. Базовый
класс `AbstractDatabaseClient` содержит логику ожидания появления записей с
повторами и формирует аттачи в Allure. Конкретные клиенты, например
`WalletDatabaseClient` и `CoreDatabaseClient`, расширяют его и используют
репозитории Spring.

### 2. Как настроить подключение

Запускайте тесты с системным свойством `-Denv=<имя_окружения>`. В каталоге
`configs` в разделе `databases` задаются адреса хостов, учётные данные и параметры
ожидания.

### 3. Где прописать адрес сервера

Параметры подключения считываются классом `DynamicPropertiesConfigurator` и
передаются в `CoreDbConfig` и `WalletDbConfig`, создающие необходимые бины JPA.

### 4. Как работает абстрактный класс

Метод `awaitAndGetOrFail` в `AbstractDatabaseClient` использует Awaitility для
ожидания результата и прикладывает найденные данные в отчёт.

### 5. Подключение новой базы

1. Добавьте параметры новой базы в раздел `databases` конфигурационного файла.
2. Создайте конфигурационный класс по образцу `CoreDbConfig`.
3. Реализуйте клиент, наследующий `AbstractDatabaseClient`, и необходимые
   репозитории.

### 6. Пример клиента

```java
@Component
public class WalletDatabaseClient extends AbstractDatabaseClient {

    private final WalletRepository walletRepository;

    public WalletDatabaseClient(AllureAttachmentService attachmentService,
                                WalletRepository walletRepository,
                                ObjectMapper objectMapper) {
        super(attachmentService, objectMapper);
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public Wallet findWalletByUuidOrFail(String uuid) {
        Supplier<Optional<Wallet>> query = () ->
                Optional.ofNullable(walletRepository.findByUuid(uuid));
        return awaitAndGetOrFail("wallet by uuid " + uuid,
                "Wallet Record [" + uuid + "]", query);
    }
}
```

### 7. Использование в тестах

```java
@Autowired
private WalletDatabaseClient walletDatabaseClient;

step("DB: проверяем запись кошелька", () -> {
    var wallet = walletDatabaseClient.findWalletByUuidOrFail(testData.walletUuid);
    assertNotNull(wallet);
});
```

## Работа с NATS

### 1. Архитектура тестового клиента

Инфраструктура повторяет подход Kafka-клиента. `NatsConnectionManager` поднимает
соединение и проверяет наличие нужного стрима. Полученные `Connection` и
`JetStream` передаются в `NatsSubscriber`, который отвечает за подписку,
повторные попытки и формирование Allure-аттачей. Внутри обработчика используется
`NatsPayloadMatcher`: он преобразует payload в `Map`, применяет JSONPath-фильтры
и отдаёт результат только при полном совпадении. Сервис `NatsAttachmentHelper`
создаёт два типа аттачей — Search Info с параметрами поиска и карточки найденных
сообщений. Поверх всех этих компонентов расположен фасад `NatsClient` с fluent-API
ожиданий и настройкой таймаутов.

### 2. Настройка подключения

При запуске тестов указывайте системное свойство `-Denv=<имя_окружения>`. В файле
`configs/<env>.json` должна присутствовать секция `nats` с хостами и параметрами
подписок. Пример:

```json
"nats": {
  "hosts": ["nats-01.dev.local:4222", "nats-02.dev.local:4222"],
  "streamName": "wallet",
  "streamPrefix": "beta-09",
  "searchTimeoutSeconds": 60,
  "uniqueDuplicateWindowMs": 5000,
  "subscriptionAckWaitSeconds": 10,
  "subscriptionInactiveThresholdSeconds": 30,
  "subscriptionBufferSize": 200,
  "subscriptionRetryCount": 5,
  "subscriptionRetryDelayMs": 500
}
```

`NatsConfigProvider` считывает эту конфигурацию и передает её в `NatsConnectionManager`,
поэтому дополнительный `application.yml` для тестов не требуется.

### 3. Формирование subject

`NatsClient` поставляется с готовым helper-ом `buildWalletSubject(playerUuid, walletUuid)`,
который строит subject вида `prefix.streamName.*.<playerUuid>.<walletUuid>`.
При необходимости можно добавить собственные фабрики subject в самом клиенте
или указывать строку напрямую через `.from("<subject>")`.

### 4. Fluent API ожиданий

`NatsExpectationBuilder` предоставляет тот же DSL, что и Kafka-клиент:

* `.from(subject)` — задаёт JetStream-subject.
* `.with(jsonPath, expectedValue)` — сравнивает payload по JSONPath. Все значения
  приводятся к строке, поэтому можно смело передавать числа и перечисления.
* `.withType(typeHeader)` и `.withSequence(seq)` — фильтры по заголовку `type` и
  номеру сообщения в стриме.
* `.unique([window])` — проверяет, что найдено ровно одно сообщение, дожидаясь
  дубликатов в пределах окна.
* `.within(timeout)` — переопределяет таймаут поиска.

Все фильтры в NATS-клиенте задаются через JSONPath и метаданные. Такой подход
обеспечивает единый формат Search Info и упрощает комбинирование условий без
кастомных предикатов; поддержка `.with(BiPredicate)` полностью удалена.

### 5. Аттачи Allure

Каждый запуск `.fetch()` добавляет в отчёт:

* **Search Info** с subject, таймаутом, типом сообщения и перечнем всех фильтров
  (JSONPath и по метаданным).
* **Found Message** — содержимое найденного события в удобном формате.
* **Duplicate Message** либо **Message Not Found**, если ожидание завершилось
  неуспешно.

### 6. Примеры использования

#### Базовый пример

```java
step("NATS: ожидаем событие изменения баланса", () -> {
    String subject = natsClient.buildWalletSubject(playerId, walletId);

    NatsMessage<NatsBalanceAdjustedPayload> event = natsClient
            .expect(NatsBalanceAdjustedPayload.class)
            .from(subject)
            .withType(NatsEventType.BALANCE_ADJUSTED.getHeaderValue())
            .with("$.comment", expectedComment)
            .fetch();

    assertEquals(walletId, event.getPayload().getUuid());
});
```

#### Комплексный пример

```java
step("NATS: Ожидаем событие о создании лимита с определенным типом", () -> {
    String subject = natsClient.buildWalletSubject(playerId, walletId);

    NatsMessage<WalletLimitEvent> message = natsClient.expect(WalletLimitEvent.class)
            .from(subject)
            .withType("LimitCreatedEvent")
            .with("$.limitType", "DAILY_TRANSACTION")
            .with("$.newAmount", 10000)
            .unique()
            .fetch();

    assertNotNull(message.getPayload(), "then.nats.payload_not_null");
    assertEquals("LimitCreatedEvent", message.getType(), "then.nats.type_header.match");
});
```

Фильтры можно комбинировать в любом порядке: например, добавить `.withSequence(...)`
для жёсткого совпадения номера или несколько JSONPath-проверок подряд.

### 7. Подключение к новому subject

1. Добавьте параметры в секцию `nats` конфигурации окружения (при необходимости).
2. Реализуйте helper в `NatsClient`, если формат subject сложный.
3. Используйте `.from("<subject>")` напрямую, если достаточно одиночного шага в тесте.

### 8. Использование в тестах

Инжектируйте `NatsClient` как любой Spring-бин и вызывайте его внутри `Allure.step`:

```java
@Autowired
private NatsClient natsClient;

step("NATS: получаем событие стартовой ставки", () -> {
    natsClient.expect(NatsBettingEventPayload.class)
            .from(subject)
            .withType(NatsEventType.BETTED_FROM_IFRAME.getHeaderValue())
            .with("$.betUuid", expectedBetUuid)
            .fetch();
});
```

Такая структура делает переход между Kafka и NATS-клиентами максимально бесшовным:
в обоих случаях используются одинаковые концепции фильтрации и оформления отчёта.
## 🔗 Тестовый контекст и передача данных

Внутри тестовых методов мы используем небольшие классы `TestContext`, куда
складываем данные, полученные на разных шагах. Экземпляр такого класса
создаётся в начале теста и затем передаётся в лямбды `Allure.step`.

```java
final class TestContext {
    RegisteredPlayerData registeredPlayer;
    MakePaymentRequest betRequest;
    NatsMessage<NatsBettingEventPayload> winEvent;
}

final TestContext ctx = new TestContext();

step("Default Step: регистрация", () -> {
    ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
});

step("Manager API: ставка", () -> {
    ctx.betRequest = generateRequest(ctx.registeredPlayer.getWalletData().walletUUID());
    managerClient.makePayment(ctx.betRequest);
});
```

Такой контекст делает код теста самодокументируемым и упрощает передачу данных
между шагами, сохраняя при этом потокобезопасность.

## 🧪 Сквозной пример

Ниже приведён фрагмент теста `WinFromIframeTest.shouldProcessWinFromIframeAndVerifyEvent`,
который задействует HTTP‑клиент, NATS, Kafka, базу данных и Redis.

```java
@Test
@DisplayName("Проверка обработки выигрыша iframe")
void shouldProcessWinFromIframeAndVerifyEvent() {
    final BigDecimal adjustmentAmount = new BigDecimal("150.00");
    final BigDecimal betAmount = new BigDecimal("10.15");
    final BigDecimal winAmount = new BigDecimal("20.15");
    final class TestContext {
        RegisteredPlayerData registeredPlayer;
        MakePaymentData betInputData;
        MakePaymentRequest betRequestBody;
        NatsMessage<NatsBettingEventPayload> winEvent;
    }
    final TestContext ctx = new TestContext();

    step("Default Step: Регистрация нового пользователя", () -> {
        ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
    });

    step("Manager API: Совершение ставки на спорт", () -> {
        ctx.betInputData = MakePaymentData.builder()
                .type(NatsBettingTransactionOperation.BET)
                .playerId(ctx.registeredPlayer.getWalletData().playerUUID())
                .summ(betAmount.toPlainString())
                .couponType(NatsBettingCouponType.SINGLE)
                .currency(ctx.registeredPlayer.getWalletData().currency())
                .build();

        ctx.betRequestBody = generateRequest(ctx.betInputData);
        managerClient.makePayment(ctx.betRequestBody);
    });

    step("Manager API: Получение выигрыша", () -> {
        ctx.betRequestBody.setSumm(winAmount.toString());
        ctx.betRequestBody.setType(NatsBettingTransactionOperation.WIN);
        managerClient.makePayment(ctx.betRequestBody);
    });

    step("NATS: Проверка события", () -> {
        var subject = natsClient.buildWalletSubject(
                ctx.registeredPlayer.getWalletData().playerUUID(),
                ctx.registeredPlayer.getWalletData().walletUUID());
        ctx.winEvent = natsClient.expect(NatsBettingEventPayload.class)
                .from(subject)
                .fetch();
    });

    step("Kafka: Проверка сообщения", () -> {
        walletProjectionKafkaClient.expect(WalletProjectionMessage.class)
                .with("seq_number", ctx.winEvent.getSequence())
                .fetch();
    });

    step("DB Wallet: Проверка записи", () -> {
        walletDatabaseClient.findLatestIframeHistoryByUuidOrFail(
                ctx.winEvent.getPayload().getUuid());
    });

    step("Redis(Wallet): Проверка агрегата", () -> {
        redisWalletClient
                .key(ctx.registeredPlayer.getWalletData().walletUUID())
                .withAtLeast("LastSeqNumber", (int) ctx.winEvent.getSequence())
                .fetch();
    });
}
```
