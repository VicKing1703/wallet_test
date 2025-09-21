# Kafka Test Client

Клиент Kafka в модуле `kafka-api` предназначен для автотестов: он асинхронно ожидает сообщения в нужных топиках, проверяет их содержимое и прикладывает найденные события к отчёту Allure.

## Основные возможности

* **Fluent API.** Ожидания строятся через цепочку методов `expect(...).with(...).unique().within(...).fetch()`.
* **Автоматическое асинхронное ожидание.** Клиент ждёт появления сообщения в фоне с учётом настраиваемых таймаутов и интервалов опроса.
* **Интеграция с Allure.** В отчёт автоматически добавляются аттачи с условиями поиска, найденными сообщениями, таймаутами и ошибками десериализации.
* **Типобезопасные DTO.** Сообщения десериализуются в заранее описанные Java-записи или классы.
* **Гибкая фильтрация payload.** Фильтры задаются через JsonPath и применяются к полю любого уровня вложенности.
* **Проверка уникальности.** Можно потребовать единственное совпадение в буфере.
* **Централизованная конфигурация топиков.** Связывание DTO и топиков вынесено в отдельный реестр.

## Настройка и конфигурация

### Подключение зависимости

Добавьте модуль в зависимости тестового проекта:

```gradle
dependencies {
    implementation project(":kafka-api")
}
```

### Конфигурация Spring

Создайте бин `KafkaTopicMappingRegistry`, который сопоставляет DTO и суффиксы топиков Kafka. Пример готовой конфигурации:

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public KafkaTopicMappingRegistry kafkaTopicMappingRegistry() {
        Map<Class<?>, String> mappings = new HashMap<>();

        mappings.put(PlayerAccountMessage.class, "player.v1.account");
        mappings.put(WalletProjectionMessage.class, "wallet.v8.projectionSource");
        mappings.put(GameSessionStartMessage.class, "core.gambling.v1.GameSessionStart");
        mappings.put(LimitMessage.class, "limits.v2");
        mappings.put(PaymentTransactionMessage.class, "payment.v1.transaction");

        return new SimpleKafkaTopicMappingRegistry(mappings);
    }
}
```

`KafkaTopicMappingRegistry` определяет соответствие между DTO и суффиксом топика. Клиент использует префикс окружения и автоматически вычисляет полное имя топика перед подпиской или поиском сообщения. Все топики из этого реестра автоматически добавляются в подписку consumer — дополнительная конфигурация списков в `application.yml` больше не требуется.

### Конфигурация приложения

Раздел `kafka` в `application.yml` (или YAML-оверлей поверх JSON-конфигурации) описывает параметры подключения и поведения клиента:

```yaml
kafka:
  bootstrapServer: "kafka-development-01:9092,kafka-development-02:9092,kafka-development-03:9092"
  groupId: "cb-wallet-test-consumer-beta-09"
  bufferSize: 500
  findMessageTimeout: "PT60S"
  findMessageSleepInterval: "PT0.2S"
  pollDuration: "PT1S"
  shutdownTimeout: "PT5S"
  autoOffsetReset: "latest"
  enableAutoCommit: true
```

Краткое описание параметров:

* `bootstrapServer` — список брокеров Kafka, к которым подключается тестовый consumer.
* `groupId` — идентификатор consumer group для всех тестов окружения.
* `bufferSize` — максимальное количество сообщений, хранимых в буфере на топик; при переполнении старые записи удаляются.
* `findMessageTimeout` — таймаут ожидания сообщения по умолчанию.
* `findMessageSleepInterval` — интервал между попытками поиска при ожидании.
* `pollDuration` — максимальное время блокировки poll при чтении Kafka.
* `shutdownTimeout` — время, отведённое на корректное завершение consumer.
* `autoOffsetReset` — стратегия позиционирования (`latest` или `earliest`).
* `enableAutoCommit` — управление автоматическим коммитом offset'ов.

## Руководство по использованию

### Базовое ожидание сообщения

```java
WalletProjectionMessage message = kafkaClient.expect(WalletProjectionMessage.class)
        .fetch();
```

Метод `expect` создаёт билдер с типом сообщения, а `fetch()` запускает поиск с таймаутом по умолчанию и без фильтров.

### Фильтрация сообщений

```java
LimitMessage message = kafkaClient.expect(LimitMessage.class)
        .with("playerId", playerId)
        .with("data.player.id", playerId)
        .with("$.metadata.eventType", "CREATED")
        .fetch();
```

* Метод `.with(key, value)` добавляет фильтр. Ключ — JsonPath к полю payload. Можно указывать относительные пути (`playerId`) или явные (`$.metadata.eventType`).
* Для вложенных структур используйте dotted-path: например, `data.player.id` проверит поле `player.id` внутри объекта `data`.
* Пустые значения не добавляются в фильтр, что удобно при условных проверках.

JsonPath-фильтры применяются к каждому сообщению из буфера. Если путь не найден или значение отличается, сообщение пропускается.

### Проверка уникальности

```java
LimitMessage message = kafkaClient.expect(LimitMessage.class)
        .with("playerId", playerId)
        .unique()
        .fetch();
```

`unique()` требует, чтобы подходящее сообщение нашлось ровно в единственном экземпляре. В противном случае будет выброшено:

* `KafkaMessageNotFoundException` — если совпадения нет даже после ожидания.
* `KafkaMessageNotUniqueException` — если найдено больше одного совпадения.

### Кастомный таймаут ожидания

```java
LimitMessage message = kafkaClient.expect(LimitMessage.class)
        .with("playerId", playerId)
        .within(Duration.ofSeconds(10))
        .fetch();
```

Метод `within` переопределяет таймаут по умолчанию. Если его не вызвать, используется значение `findMessageTimeout` из конфигурации.

### Комплексный пример

Ниже фрагмент реального теста, показывающий ожидание события о создании лимита, фильтрацию по полям и последующую проверку бизнес-логики:

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

Такой сценарий можно расширить, добавив `unique()` для контроля количества сообщений и `within(Duration.ofSeconds(30))` для пошаговой отладки.

## Интеграция с Allure Report

При каждом поиске клиент автоматически формирует аттачи:

* **Search Info** — топик, источник поиска (явное имя или вывод по типу), ожидаемый DTO и набор фильтров.
* **Found Message** — отформатированное содержимое найденного сообщения (partition, offset, timestamp, prettified payload).
* **Message Not Found** — сводка условий, по которым сообщение не найдено к моменту таймаута.
* **Deserialization Error** — текст ошибки Jackson и исходный payload, который не удалось преобразовать в DTO.

Эти вложения облегчают анализ упавших тестов без чтения логов.

## Архитектура

* **KafkaClient** — публичный фасад. Создаёт билдер ожиданий и передаёт ему фонового consumer и таймауты.
* **KafkaExpectationBuilder** — fluent-конструктор. Сохраняет фильтры, таймаут, флаг уникальности и выполняет поиск с проверками.
* **KafkaBackgroundConsumer** — управляет подписками, выполняет асинхронный поиск в буфере и взаимодействует с Allure.
* **KafkaPollingService** — низкоуровневый сервис Spring Kafka, который подписывается на топики, сразу смещает consumer в конец и складывает записи в буфер.
* **MessageBuffer** — потокобезопасный буфер на каждый топик. Следит за переполнением и хранит ограниченное количество сообщений.
* **MessageFinder** — применяет JsonPath-фильтры, десериализует сообщения и возвращает совпадения, а также сообщает об ошибках десериализации.

## FAQ / Troubleshooting

**Вопрос:** Тест падает с `KafkaMessageNotFoundException`, хотя сообщение точно отправлено. Что проверить?

1. Убедитесь, что фильтры `.with(...)` совпадают с фактическим payload (особенно регистр и формат чисел).
2. Проверьте, что суффикс топика для нужного DTO добавлен в `KafkaTopicMappingRegistry`.
3. Убедитесь, что префикс топика (environment prefix) совпадает с фактическим именем топика.
4. Увеличьте таймаут через `.within(...)` для дополнительной диагностики.
5. Просмотрите логи на наличие ошибок подключения к Kafka или десериализации.

**Вопрос:** Добавил новый DTO, но клиент не находит топик.

Убедитесь, что в бине `KafkaTopicMappingRegistry` добавлен маппинг `mappings.put(MyNewMessage.class, "my.new.topic.suffix")`. Без этого клиент не сможет вычислить полное имя топика.
