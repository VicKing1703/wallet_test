# План переноса HTTP-инфраструктуры в модуль `kafka-api`

## 1. Цели и ограничения
- Сделать `kafka-api` универсальным модулем, который можно подключить к любому проекту и получить готовую HTTP-инфраструктуру без изменения текущей архитектуры конфигурации.
- Перенести конфигурационный и инфраструктурный код (`config`, логирование, общие исключения) из тестового модуля в `kafka-api`, при этом **DTO и Feign-клиенты остаются** в тестовом модуле.
- Сохранить обратную совместимость по настройкам: существующие тесты, завязанные на свойства `app.api.*`, должны продолжить работать.

## 2. Обзор текущего состояния
- HTTP-конфигурация и логгер находятся в `src/test/java/com/uplatform/wallet_tests/api/http/config` вместе с исключениями (`exceptions`).
- `AllureFeignLoggerConfig` и `AllureFeignLogger` используют `AllureAttachmentService`, который уже живёт в `kafka-api` и экспортируется в виде бина.
- Автоконфигурации `kafka-api` перечислены в `META-INF/spring.factories`, сейчас там только `RedisApiAutoConfiguration`.
- Конфигурационные классы `EnvironmentConfigurationProvider`, `DynamicPropertiesConfigurator`, `ApiConfig` и др. расположены в `kafka-api` и уже публикуют настройки HTTP в виде свойств `app.api.*`.

## 3. Подготовительный аудит
1. **Инвентаризация кода для переноса**
   - Пакеты: `config`, `exceptions`, общие утилиты HTTP (без `dto` и `client`).
   - Проверить на статические зависимости (например, логику, которая подтягивает классы DTO напрямую).
2. **Проверка зависимостей**
   - Убедиться, что `kafka-api` получает `spring-cloud-starter-openfeign`, `feign-okhttp`, `okhttp3`, `spring-boot-starter-web` (для автоконфигурации Jackson/Feign).
   - Анализировать дубли в корневом `build.gradle`, чтобы после переноса оставить в корне только то, что нужно клиентам/тестам.
3. **Конфигурация окружений**
   - Пересмотреть `ApiConfig` и планы по стандартизации JSON (обновление секции `http` + алиасы для `app.api.*`).

## 4. Целевое состояние
- В `kafka-api` появляется пакет `com.uplatform.wallet_tests.api.http` c подпакетами `config` и `exceptions`.
- Добавлена автоконфигурация `HttpApiAutoConfiguration`, регистрирующая бин `Feign.Builder`, `Request.Options`, `AllureFeignLogger`, алиасы свойств и т.д.
- В `META-INF/spring.factories` перечислены `RedisApiAutoConfiguration` и новая `HttpApiAutoConfiguration`.
- Тестовый модуль подключает инфраструктуру из `kafka-api`, сохраняя свои Feign-клиенты и DTO.
- В JSON-конфигурации используется новая структура `http.services.*`, но `DynamicPropertiesConfigurator` продолжает публиковать `app.api.*` для обратной совместимости.

## 5. Пошаговый план работ
1. **Подготовить зависимости и структуру проекта**
   - Добавить в `kafka-api/build.gradle` зависимости: `spring-boot-starter-web`, `spring-cloud-starter-openfeign`, `feign-okhttp`, `okhttp3`, `spring-boot-starter-aop` (если требуется аспект/логгирование).
   - Убедиться, что Lombok подключён (уже есть `compileOnly/annotationProcessor`).
   - Создать пакеты `api/http/config` и `api/http/exceptions` в `kafka-api`.

2. **Перенести инфраструктурные классы**
   - Переместить `AllureFeignLogger`, `AllureFeignLoggerConfig`, `FeignClientConfiguration` (если есть), исключения (`FeignClientException`, `FeignResponseValidationException` и т.д.) в `kafka-api`.
   - Обновить пакеты/импорты, скорректировать модификаторы доступа (должны быть `public`).
   - Привязать все классы к уже существующему `AllureAttachmentService` из `kafka-api`.

3. **Настроить автоконфигурацию**
   - Создать класс `HttpApiAutoConfiguration` с аннотацией `@Configuration` и условием `@ConditionalOnClass(Feign.class)`.
   - Регистировать нужные бины: `Decoder`, `Encoder`, `Contract`, `Feign.Builder`, `Logger`, `Retryer`, `ErrorDecoder`, `Request.Options`.
   - Использовать `EnvironmentConfigurationProvider`/`DynamicPropertiesConfigurator` для получения параметров таймаутов и алиасов свойств (например, `app.http.services.<id>.base-url`).
   - Добавить новую автоконфигурацию в `spring.factories` (`EnableAutoConfiguration`).

4. **Расширить работу с конфигурацией**
   - Разработать модели `HttpModuleProperties`, `HttpServiceProperties`, `HttpClientCredentials` (по итогам стандартизации).
   - Добавить бин `@ConfigurationProperties("app.http")` для публикации настроек.
   - В `DynamicPropertiesConfigurator` реализовать генерацию алиасов `app.api.*` => `app.http.services.*` и наоборот.
   - Реализовать миграционный слой: если в JSON остался старый блок `api`, преобразовать его в новые свойства при загрузке.

5. **Обновить тестовый модуль**
   - Заменить локальные импорты на классы из `kafka-api`.
   - Удалить перенесённые классы из `src/test/java`.
   - Проверить, что Feign-клиенты используют `FeignClient` с `configuration = HttpApiFeignConfig.class` (если такая конфигурация нужна) и что она приезжает из `kafka-api`.
   - Скорректировать зависимости: если `spring-cloud-starter-openfeign` больше не нужен на уровне приложения, оставить его только через `kafka-api`.

6. **Регресс и документация**
   - Обновить `README` (раздел HTTP) с информацией о новом модуле и настройках.
   - Добавить документацию по формату JSON (`http.services`).
   - Запустить регрессионные тесты (юнит/интеграционные) + smoke-тесты HTTP клиентов.

## 6. Риски и меры
- **Разрыв конфигурации**: не все проекты могут сразу перейти на `http.services`. Решение: алиасы и миграционный слой в `DynamicPropertiesConfigurator`.
- **Отсутствие зависимостей**: новые пользователи `kafka-api` должны автоматически получить Feign/OkHttp. Решение: объявить их `api`-зависимостями в Gradle или задокументировать необходимость.
- **Конфликты бинов**: убедиться, что новая автоконфигурация условно создаёт бины только если их нет в контексте (`@ConditionalOnMissingBean`).

## 7. Критерии готовности
- Все HTTP-тесты проходят без модификации клиентов/DTO.
- Новый проект может подключить `kafka-api` и получить готовую HTTP-инфраструктуру, указав только JSON окружения.
- Документация обновлена и описывает новую схему конфигурации.
- Конфигурационные свойства доступны и в старом (`app.api.*`), и в новом (`app.http.*`) пространствах имён.

## 8. Следующие шаги
1. Утвердить план с командой.
2. Создать техническую задачу в бэклоге с чек-листом из раздела 5.
3. Начать реализацию с блока «Подготовить зависимости и структуру проекта».
