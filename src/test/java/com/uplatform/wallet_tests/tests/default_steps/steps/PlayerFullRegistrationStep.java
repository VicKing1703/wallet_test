package com.uplatform.wallet_tests.tests.default_steps.steps;

import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.cancel_kyc_check.CancelKycCheckRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.update_verification_status.UpdateVerificationStatusRequest;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactType;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactVerificationRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.identity.IdentityVerificationRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.identity.VerificationStatus;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.FullRegistrationRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.BonusChoiceType;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.Gender;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SetSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactTypedRequest;
import com.uplatform.wallet_tests.api.kafka.client.KafkaClient;
import com.uplatform.wallet_tests.api.kafka.dto.PlayerAccountMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.redis.GenericRedisClient;
import com.uplatform.wallet_tests.api.redis.model.WalletData;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.CapAdminTokenStorage;
import com.uplatform.wallet_tests.api.db.PlayerDatabaseClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.GeneratorType.*;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Component
public class PlayerFullRegistrationStep {

    private final FapiClient publicClient;
    private final CapAdminClient capAdminClient;
    private final KafkaClient kafkaClient;
    private final GenericRedisClient<Map<String, WalletData>> redisPlayerClient;
    private final GenericRedisClient<WalletFullData> redisWalletClient;
    private final CapAdminTokenStorage tokenStorage;
    private final PlayerDatabaseClient playerDatabaseClient;
    private final String defaultCurrency;
    private final String defaultCountry;
    private final String platformNodeId;

    @Autowired
    public PlayerFullRegistrationStep(
            FapiClient publicClient,
            CapAdminClient capAdminClient,
            KafkaClient kafkaClient,
            @Qualifier("redisPlayerClient") GenericRedisClient<Map<String, WalletData>> redisPlayerClient,
            @Qualifier("redisWalletClient") GenericRedisClient<WalletFullData> redisWalletClient,
            CapAdminTokenStorage tokenStorage,
            PlayerDatabaseClient playerDatabaseClient,
            @Value("${app.settings.default.currency}") String defaultCurrency,
            @Value("${app.settings.default.country}") String defaultCountry,
            @Value("${app.settings.default.platform-node-id}") String platformNodeId) {
        this.publicClient = Objects.requireNonNull(publicClient);
        this.capAdminClient = Objects.requireNonNull(capAdminClient);
        this.kafkaClient = Objects.requireNonNull(kafkaClient);
        this.redisPlayerClient = Objects.requireNonNull(redisPlayerClient);
        this.redisWalletClient = Objects.requireNonNull(redisWalletClient);
        this.tokenStorage = Objects.requireNonNull(tokenStorage);
        this.playerDatabaseClient = Objects.requireNonNull(playerDatabaseClient);
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency);
        this.defaultCountry = Objects.requireNonNull(defaultCountry);
        this.platformNodeId = Objects.requireNonNull(platformNodeId);
    }

    private static final class TestContext {
        ContactVerificationRequest verificationRequest;
        PlayerAccountMessage confirmationMessage;
        ResponseEntity<VerifyContactResponse> verifyContactResponse;
        FullRegistrationRequestBody fullRegistrationRequest;
        PlayerAccountMessage fullRegistrationMessage;
        ResponseEntity<TokenCheckResponse> authorizationResponse;
        IdentityVerificationRequest identityRequest;
        VerificationStatus verificationStatus;
        ContactVerificationRequest emailVerificationRequest;
        PlayerAccountMessage emailConfirmationMessage;
        ResponseEntity<VerifyContactResponse> emailVerifyResponse;
        WalletData playerWalletData;
        WalletFullData updatedWalletData;
    }

    public RegisteredPlayerData registerNewPlayerWithKyc() {
        final TestContext ctx = new TestContext();

        step("Public API: Запрос на верификацию телефона", () -> {
            ctx.verificationRequest = ContactVerificationRequest.builder()
                    .contact(get(PHONE))
                    .type(ContactType.PHONE)
                    .build();
            var response = this.publicClient.requestVerification(
                    ctx.verificationRequest);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.request_verification.status_code");
        });

        step("Kafka: Ожидание и получение OTP кода", () -> {
            ctx.confirmationMessage = this.kafkaClient.expect(PlayerAccountMessage.class)
                    .with("message.eventType", "player.confirmationPhone")
                    .with("player.phone", ctx.verificationRequest.getContact().substring(1))
                    .fetch();
            assertNotNull(ctx.confirmationMessage, "kafka.phone_confirmation.message");
            assertNotNull(ctx.confirmationMessage.context(), "kafka.phone_confirmation.context");
            assertNotNull(ctx.confirmationMessage.context().confirmationCode(), "kafka.phone_confirmation.code");
        });

        step("Public API: Подтверждение номера телефона", () -> {
            var request = VerifyContactRequest.builder()
                    .contact(ctx.verificationRequest.getContact().substring(1))
                    .code(ctx.confirmationMessage.context().confirmationCode())
                    .build();
            ctx.verifyContactResponse = this.publicClient.verifyContact(request);
            assertEquals(HttpStatus.OK, ctx.verifyContactResponse.getStatusCode(), "fapi.verify_contact.status_code");
            assertNotNull(ctx.verifyContactResponse.getBody(), "fapi.verify_contact.body_not_null");
            assertNotNull(ctx.verifyContactResponse.getBody().getHash(), "fapi.verify_contact.hash");
        });

        step("Public API: Выполнение полной регистрации пользователя", () -> {
            ctx.fullRegistrationRequest = FullRegistrationRequestBody.builder()
                    .currency(this.defaultCurrency)
                    .country(this.defaultCountry)
                    .bonusChoice(BonusChoiceType.NONE)
                    .phone(ctx.verificationRequest.getContact().substring(1))
                    .phoneConfirmation(ctx.verifyContactResponse.getBody().getHash())
                    .firstName(get(NAME))
                    .lastName(get(NAME))
                    .birthday("1991-05-15")
                    .gender(Gender.MALE)
                    .personalId(get(PERSONAL_ID))
                    .iban(get(IBAN))
                    .city("Riga")
                    .permanentAddress("Brivibas iela 1")
                    .postalCode("LV-1010")
                    .profession("Developer")
                    .password(get(PASSWORD))
                    .rulesAgreement(true)
                    .context(Collections.emptyMap())
                    .build();
            var fullRegResponse = this.publicClient.fullRegistration(ctx.fullRegistrationRequest);
            assertEquals(HttpStatus.CREATED, fullRegResponse.getStatusCode(), "fapi.full_registration.status_code");
        });

        step("Kafka: Получение сообщения о регистрации", () -> {
            ctx.fullRegistrationMessage = this.kafkaClient.expect(PlayerAccountMessage.class)
                    .with("message.eventType", "player.signUpV2Full")
                    .with("player.phone", ctx.verificationRequest.getContact().substring(1))
                    .fetch();
            assertNotNull(ctx.fullRegistrationMessage, "kafka.player_signup.message");
            assertNotNull(ctx.fullRegistrationMessage.player(), "kafka.player_signup.player");
            assertNotNull(ctx.fullRegistrationMessage.player().externalId(), "kafka.player_signup.player_external_id");
        });

        step("Public API: Авторизация", () -> {
            var tokenRequest = TokenCheckRequest.builder()
                    .username(ctx.fullRegistrationMessage.player().accountId())
                    .password(ctx.fullRegistrationRequest.getPassword())
                    .build();
            ctx.authorizationResponse = this.publicClient.tokenCheck(tokenRequest);
            assertEquals(HttpStatus.OK, ctx.authorizationResponse.getStatusCode(), "fapi.token_check.status_code");
            assertNotNull(ctx.authorizationResponse.getBody(), "fapi.token_check.body_not_null");
            assertNotNull(ctx.authorizationResponse.getBody().getToken(), "fapi.token_check.token");
        });

        step("Redis (Player): Получение основного кошелька игрока", () -> {
            String walletSelector = String.format(
                    "$.*[?(@.currency == '%s' && @.type == %d && @.status == %d)]",
                    this.defaultCurrency,
                    1,
                    1
            );

            Map<String, WalletData> wallets = this.redisPlayerClient
                    .key(ctx.fullRegistrationMessage.player().externalId())
                    .with(walletSelector, value -> value instanceof List<?> list && !list.isEmpty(),
                            "contains wallet matching criteria")
                    .within(Duration.ofSeconds(30))
                    .fetch();

            Optional<WalletData> playerWallet = wallets.values().stream()
                    .filter(wallet -> wallet != null
                            && Objects.equals(wallet.currency(), this.defaultCurrency)
                            && Objects.equals(wallet.type(), 1)
                            && Objects.equals(wallet.status(), 1))
                    .findFirst();

            assertTrue(playerWallet.isPresent(), "redis.player.wallet.not_found");

            ctx.playerWalletData = playerWallet.orElseThrow();
            assertNotNull(ctx.playerWalletData.walletUUID(), "redis.player.wallet.uuid");
            assertNotNull(ctx.playerWalletData.currency(), "redis.player.wallet.currency");
        });

        step("Public API: Создание запроса на подтверждение личности", () -> {
            var issuedMillis = java.time.LocalDateTime.now()
                    .minusYears(10)
                    .toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli();
            var expiryMillis = java.time.LocalDateTime.now()
                    .plusYears(10)
                    .toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli();
            var request = IdentityVerificationRequest.builder()
                    .number(get(NUMBER, 9))
                    .type("4")
                    .issuedDate(String.valueOf(issuedMillis / 1000))
                    .expiryDate(String.valueOf(expiryMillis / 1000))
                    .build();
            ctx.identityRequest = request;
            var response = publicClient.createIdentityVerification(
                    ctx.authorizationResponse.getBody().getToken(),
                    request.getNumber(),
                    request.getType(),
                    request.getIssuedDate(),
                    request.getExpiryDate());
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.create_identity_verification.status_code");
        });

        step("Public API: Получение статуса верификации", () -> {
            var response = publicClient.getVerificationStatus(
                    ctx.authorizationResponse.getBody().getToken());
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_verification_status.status_code");
            assertNotNull(response.getBody(), "fapi.get_verification_status.body_not_null");
            assertFalse(response.getBody().isEmpty(), "fapi.get_verification_status.body_not_empty");
            ctx.verificationStatus = response.getBody().get(0);
        });

        step("CAP API: Обновление статуса верификации", () -> {
            var request = UpdateVerificationStatusRequest.builder()
                    .note("")
                    .reason("")
                    .status(2)
                    .build();
            var response = capAdminClient.updateVerificationStatus(
                    ctx.verificationStatus.getDocumentId(),
                    tokenStorage.getAuthorizationHeader(),
                    platformNodeId,
                    request);
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap.update_verification_status.status_code");
        });

        step("Public API: Запрос верификации email", () -> {
            ctx.emailVerificationRequest = ContactVerificationRequest.builder()
                    .contact(get(EMAIL))
                    .type(ContactType.EMAIL)
                    .build();
            var response = publicClient.requestVerification(ctx.emailVerificationRequest);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.request_verification_email.status_code");
        });

        step("Kafka: Получение сообщения о подтверждении email", () -> {
            ctx.emailConfirmationMessage = kafkaClient.expect(PlayerAccountMessage.class)
                    .with("message.eventType", "player.confirmationEmail")
                    .with("player.email", ctx.emailVerificationRequest.getContact())
                    .fetch();
            assertNotNull(ctx.emailConfirmationMessage, "kafka.email_confirmation.message");
            assertNotNull(ctx.emailConfirmationMessage.context(), "kafka.email_confirmation.context");
            assertNotNull(ctx.emailConfirmationMessage.context().confirmationCode(), "kafka.email_confirmation.code");
        });

        step("Public API: Подтверждение email", () -> {
            var request = VerifyContactTypedRequest.builder()
                    .contact(ctx.emailVerificationRequest.getContact())
                    .type(ContactType.EMAIL)
                    .code(ctx.emailConfirmationMessage.context().confirmationCode())
                    .build();
            var response = publicClient.verifyContactWithAuth(
                    ctx.authorizationResponse.getBody().getToken(),
                    request);
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.verify_contact_email.status_code");
        });

        step("Public API: Установка лимита на одиночную ставку", () -> {
            var amount = new BigDecimal("100000.00");
            var request = SetSingleBetLimitRequest.builder()
                    .currency(ctx.playerWalletData.currency())
                    .amount(amount.toString())
                    .build();
            var response = publicClient.setSingleBetLimit(
                    ctx.authorizationResponse.getBody().getToken(),
                    request);
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_single_bet_limit.status_code");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var amount = new BigDecimal("100000.00");
            var request = SetTurnoverLimitRequest.builder()
                    .currency(ctx.playerWalletData.currency())
                    .type(NatsLimitIntervalType.DAILY)
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .amount(amount.toString())
                    .build();
            var response = publicClient.setTurnoverLimit(
                    ctx.authorizationResponse.getBody().getToken(),
                    request);
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");
        });

        step("CAP API: Отмена KYC проверки", () -> {
            var request = CancelKycCheckRequest.builder()
                    .kycCheckProceed(false)
                    .build();
            var response = capAdminClient.cancelKycCheck(
                    ctx.fullRegistrationMessage.player().externalId(),
                    tokenStorage.getAuthorizationHeader(),
                    platformNodeId,
                    request);
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap.cancel_kyc_check.status_code");
        });

        step("Redis (Wallet): Получение и проверка полных данных кошелька", () -> {
            ctx.updatedWalletData = this.redisWalletClient
                    .key(ctx.playerWalletData.walletUUID())
                    .fetch();
            assertNotNull(ctx.updatedWalletData, "redis.wallet.full_data_not_found");
            assertNotNull(ctx.updatedWalletData.playerUUID(), "redis.wallet.player_uuid");
        });

        step("DB (Player): Получение статусов свойств аккаунта", () -> {
            var property = playerDatabaseClient.waitForAccountPropertyStatus(
                    ctx.updatedWalletData.playerUUID(),
                    "REQUIRED_LIMITS_SET",
                    1,
                    Duration.ofMinutes(2));
            assertEquals(Integer.valueOf(1), property.get("status"),
                    "db.player.account_properties.required_limits_set");
        });

        return new RegisteredPlayerData(
                ctx.authorizationResponse,
                ctx.updatedWalletData);
    }

}
