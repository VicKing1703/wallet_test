package com.uplatform.wallet_tests.api.http.fapi.client;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.CasinoLossLimit;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactVerificationResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactVerificationRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.FullRegistrationRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.get_games.GetGamesResponseBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameResponseBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.player_restrictions.PlayerRestrictionResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.player_restrictions.PlayerRestrictionsRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SetSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.UpdateSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.deposit.SetDepositLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.deposit.DepositLimit;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SingleBetLimit;
import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.TurnoverLimit;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.UpdateTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.identity.VerificationStatus;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactTypedRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.DepositRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.payment.WithdrawalRequestBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

import java.util.List;

@FeignClient(name = "publicClientApi", url = "${app.api.fapi.base-url}")
public interface FapiClient {
    @PostMapping("/_front_api/api/v1/contacts/request-verification")
    ResponseEntity<ContactVerificationResponse> requestVerification(
            @RequestBody ContactVerificationRequest request);

    @PostMapping("/_front_api/api/v1/contacts/verification")
    ResponseEntity<VerifyContactResponse> verifyContact(
            @RequestBody VerifyContactRequest request);

    @PostMapping("/_front_api/api/v1/contacts")
    ResponseEntity<Void> verifyContactWithAuth(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody VerifyContactTypedRequest request);

    @PostMapping("/_front_api/api/v2/registration/full")
    ResponseEntity<Void> fullRegistration(
            @RequestBody FullRegistrationRequestBody request);

    @PostMapping("/_front_api/api/token/check")
    ResponseEntity<TokenCheckResponse> tokenCheck(
            @RequestBody TokenCheckRequest request);

    @GetMapping("/_front_api/api/v1/gambling/games")
    ResponseEntity<GetGamesResponseBody> getGames(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "perPage", required = false, defaultValue = "24") int perPage
    );

    @PostMapping("/_front_api/api/v1/gambling/games/alias/{gameAlias}/launch")
    ResponseEntity<LaunchGameResponseBody> launchGame(
            @PathVariable("gameAlias") String gameAlias,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody LaunchGameRequestBody requestBody
    );

    @PostMapping("/_front_api/api/v1/player/single-limits/single-bet")
    ResponseEntity<Void> setSingleBetLimit(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody SetSingleBetLimitRequest request
    );

    @PostMapping("/_front_api/api/v1/player/recalculated-limits/casino-loss")
    ResponseEntity<Void> setCasinoLossLimit(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody SetCasinoLossLimitRequest request
    );

    @PostMapping("/_front_api/api/v1/player/recalculated-limits/turnover-of-funds")
    ResponseEntity<Void> setTurnoverLimit(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody SetTurnoverLimitRequest request
    );

    @PostMapping("/_front_api/api/v1/player/recalculated-limits/deposit")
    ResponseEntity<Void> setDepositLimit(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody SetDepositLimitRequest request
    );

    @PostMapping("/_front_api/api/v1/player/restrictions")
    ResponseEntity<PlayerRestrictionResponse> getPlayerRestrictions(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody PlayerRestrictionsRequest request
    );

    @GetMapping("/_front_api/api/v1/player/single-limits/single-bet")
    ResponseEntity<List<SingleBetLimit>> getSingleBetLimits(
            @RequestHeader("Authorization")  String authorizationHeader
    );

    @PatchMapping("/_front_api/api/v1/player/single-limits/{limitId}")
    ResponseEntity<Void> updateSingleLimit(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("limitId") String limitId,
            @RequestBody UpdateSingleBetLimitRequest request
    );

    @PatchMapping("/_front_api/api/v1/player/recalculated-limits/{limitId}")
    ResponseEntity<Void> updateRecalculatedLimit(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("limitId") String limitId,
            @RequestBody UpdateTurnoverLimitRequest request
    );

    @GetMapping("/_front_api/api/v1/player/recalculated-limits/casino-loss")
    ResponseEntity<List<CasinoLossLimit>> getCasinoLossLimits(
            @RequestHeader("Authorization") String authorizationHeader
    );

    @GetMapping("/_front_api/api/v1/player/recalculated-limits/turnover-of-funds")
    ResponseEntity<List<TurnoverLimit>> getTurnoverLimits(
            @RequestHeader("Authorization") String authorizationHeader
    );

    @GetMapping("/_front_api/api/v1/player/recalculated-limits/deposit")
    ResponseEntity<List<DepositLimit>> getDepositLimits(
            @RequestHeader("Authorization") String authorizationHeader
    );

    @PostMapping(value = "/_front_api/api/v1/player/verification/identity", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Void> createIdentityVerification(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestPart("number") String number,
            @RequestPart("type") String type,
            @RequestPart("issuedDate") String issuedDate,
            @RequestPart("expiryDate") String expiryDate
    );

    @GetMapping("/_front_api/api/v1/player/verification/status")
    ResponseEntity<List<VerificationStatus>> getVerificationStatus(
            @RequestHeader("Authorization") String authorizationHeader
    );

    @PostMapping("/_front_api/api/v2/payment/deposit")
    ResponseEntity<Void> deposit(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody DepositRequestBody request
    );

    @PostMapping("/_front_api/api/v3/payment/withdrawal")
    ResponseEntity<Void> withdrawal(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody WithdrawalRequestBody request
    );
}

