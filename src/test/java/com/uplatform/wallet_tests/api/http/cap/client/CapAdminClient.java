package com.uplatform.wallet_tests.api.http.cap.client;

import com.uplatform.wallet_tests.api.http.cap.dto.brand.*;
import com.uplatform.wallet_tests.api.http.cap.dto.cancel_kyc_check.CancelKycCheckRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.categories.CreateCategoryResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.check.CapTokenCheckRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.check.CapTokenCheckResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v1.*;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.CreateCategoryRequestV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.CreateCategoryResponseV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.GetCategoryResponseV2;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2.PatchCategoryRequestV2;
import com.uplatform.wallet_tests.api.http.cap.dto.get_block_amount_list.BlockAmountListResponseBody;
import com.uplatform.wallet_tests.api.http.cap.dto.get_blockers.GetBlockersResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.get_player_limits.GetPlayerLimitsResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.labels.*;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.update_player_properties.UpdatePlayerPropertiesRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.update_verification_status.UpdateVerificationStatusRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "capAdminClient", url = "${app.api.cap.base-url}")
public interface CapAdminClient {

    @PatchMapping("/_cap/player/api/v1/admin/players/{playerId}/properties")
    ResponseEntity<Void> cancelKycCheck(
            @PathVariable("playerId") String playerId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody CancelKycCheckRequest request
    );

    @PatchMapping("/_cap/player/api/v1/admin/players/{playerId}/properties")
    ResponseEntity<Void> updatePlayerProperties(
            @PathVariable("playerId") String playerId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestHeader("Platform-Userid") String platformUserId,
            @RequestHeader("Platform-Username") String platformUsername,
            @RequestBody UpdatePlayerPropertiesRequest request
    );

    @PostMapping("/_cap/api/token/check")
    ResponseEntity<CapTokenCheckResponse> getToken(@RequestBody CapTokenCheckRequest request);

    @PostMapping("/_cap/api/v1/wallet/{playerUUID}/create-balance-adjustment")
    ResponseEntity<Void> createBalanceAdjustment(
            @PathVariable("playerUUID") String walletId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestHeader("Platform-Userid") String userId,
            @RequestBody CreateBalanceAdjustmentRequest request
    );

    @PatchMapping("/_cap/api/v1/players/{playerUUID}/blockers")
    ResponseEntity<Void> updateBlockers(
            @PathVariable("playerUUID") String playerUUID,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody UpdateBlockersRequest request
    );

    @GetMapping("/_cap/api/v1/players/{playerUUID}/blockers")
    ResponseEntity<GetBlockersResponse> getBlockers(
            @PathVariable("playerUUID") String playerUUID,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @GetMapping(
            value = "/_cap/api/v1/player/{playerID}/limits",
            params = {"sort", "page", "perPage"}
    )
    ResponseEntity<GetPlayerLimitsResponse> getPlayerLimits(
            @PathVariable("playerID") String playerId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestParam(name = "sort", defaultValue = "status") String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "perPage", defaultValue = "10") int perPage
    );
    default ResponseEntity<GetPlayerLimitsResponse> getPlayerLimits(
            String playerId,
            String authorizationHeader,
            String platformNodeId) {
        return getPlayerLimits(playerId, authorizationHeader, platformNodeId,
                "status", 1, 10);
    }

    @PostMapping("/_cap/api/v1/wallet/{playerUuid}/create-block-amount")
    ResponseEntity<CreateBlockAmountResponse> createBlockAmount(
            @PathVariable("playerUuid") String playerUuid,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody CreateBlockAmountRequest request
    );

    @PostMapping("/_cap/api/v2/categories")
    ResponseEntity<CreateCategoryResponse> createCategory(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-Userid") String platformUserId,
            @RequestHeader("Platform-Username") String platformUsername,
            @RequestBody CreateCategoryRequest request
    );

    @DeleteMapping("/_cap/api/v2/categories/{categoryUuid}")
    ResponseEntity<Void> deleteCategory(
            @PathVariable("categoryUuid") String categoryUuid,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-Userid") String platformUserId,
            @RequestHeader("Platform-Username") String platformUsername
    );

    @GetMapping("/_cap/api/v1/wallet/{player_uuid}/block-amount-list")
    ResponseEntity<BlockAmountListResponseBody> getBlockAmountList(
            @RequestHeader("Authorization")     String authorizationHeader,
            @RequestHeader("Platform-NodeID")   String platformNodeId,
            @PathVariable("player_uuid")        String playerUuid);
            
    @DeleteMapping("/_cap/api/v1/wallet/delete-amount-block/{block_uuid}")
    ResponseEntity<Void> deleteBlockAmount(
            @PathVariable("block_uuid") String blockUuid,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestParam("walletId") String walletId,
            @RequestParam("playerId") String playerId
    );

    @PatchMapping("/_cap/api/v1/players/verification/{documentId}")
    ResponseEntity<Void> updateVerificationStatus(
            @PathVariable("documentId") String documentId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody UpdateVerificationStatusRequest request
    );

    /**
     * <h5>Categories</h5>
     * <ol>
     * <li>Create game category</li>
     * <li>Create game category V2</li>
     * <li>Get game category by id</li>
     * <li>Get game category by id V2</li>
     * <li>Delete game category</li>
     * <li>Delete game category V2</li>
     * <li>Update game category</li>
     * <li>Update game category V2</li>
     * <li>Update game category status</li>
     * <li>Bind game to game category</li>
     * </ol>
    **/

    @PostMapping("/_cap/api/v1/categories")
    ResponseEntity<CreateCategoryResponse> createCategory(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody CreateCategoryRequest request
    );

    @PostMapping("/_cap/api/v2/categories")
    ResponseEntity<CreateCategoryResponseV2> createCategoryV2(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody CreateCategoryRequestV2 request
    );

    @GetMapping ("/_cap/api/v1/categories/{categoryId}")
    ResponseEntity<GetCategoryResponse> getCategoryById(
            @PathVariable("categoryId") String categoryId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @GetMapping ("/_cap/api/v2/categories/{categoryId}")
    ResponseEntity<GetCategoryResponseV2> getCategoryByIdV2(
            @PathVariable("categoryId") String categoryId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @DeleteMapping("/_cap/api/v1/categories/{categoryId}")
    ResponseEntity<Void> deleteCategory(
            @PathVariable("categoryId") String categoryId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @DeleteMapping("/_cap/api/v2/categories/{categoryId}")
    ResponseEntity<Void> deleteCategoryV2(
            @PathVariable("categoryId") String categoryId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @PatchMapping("/_cap/api/v1/categories/{categoryId}")
    ResponseEntity<Void> patchCategory(
            @PathVariable("categoryId") String categoryId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody PatchCategoryRequest request
    );

    @PatchMapping("/_cap/api/v2/categories/{categoryId}")
    ResponseEntity<Void> patchCategoryV2(
            @PathVariable("categoryId") String categoryId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody PatchCategoryRequestV2 request
    );

    @PatchMapping("/_cap/api/v1/categories/{categoryId}/status")
    ResponseEntity<Void> patchCategoryStatusRequest(
            @PathVariable("categoryId") String categoryId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody PatchCategoryStatusRequest request
    );

    @PostMapping("/_cap/api/v1/categories/{categoryId}/bind-game")
    ResponseEntity<Void> bindGameCategory(
            @PathVariable("categoryId") String categoryId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody BindGameCategoryRequest request
    );

    /**
     * <h5>Brands</h5>
     * <ol>
     *  <li>Create brand</li>
     *  <li>Get brand by id</li>
     *  <li>Delete brand</li>
     *  <li>Update brand</li>
     *  <li>Update brand status</li>
     *  <li>Bind game to brand</li>
     * </ol>
     */

    @PostMapping("/_cap/api/v1/brands")
    ResponseEntity<CreateBrandResponse> createBrand(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody CreateBrandRequest request
    );

    @GetMapping ("/_cap/api/v1/brands/{brandId}")
    ResponseEntity<GetBrandResponse> getBrandId(
            @PathVariable("brandId") String brandId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @DeleteMapping("/_cap/api/v1/brands/{brandId}")
    ResponseEntity<Void> deleteBrand(
            @PathVariable("brandId") String brandId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @PatchMapping("/_cap/api/v1/brands/{brandId}")
    ResponseEntity<Void> patchBrand(
            @PathVariable("brandId") String brandId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody PatchBrandRequest request
    );

    @PatchMapping("_cap/api/v1/brands/{brandId}/status")
    ResponseEntity<Void> patchStatusBrand(
            @PathVariable("brandId") String brandId,
            @RequestHeader("Authtorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody PatchStatusBrandRequest request
    );

    @PostMapping("/_cap/api/v1/brands/{brandId}/bind-game")
    ResponseEntity<Void> bindGameBrand(
            @PathVariable("brandId") String brandId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody BindGameBrandRequest request
    );

    /**
     * <h5>Labels</h5>
     * <ol>
     *  <li>Create game label</li>
     *  <li>Get game label by id</li>
     *  <li>Delete game label</li>
     *  <li>Update game label</li>
     *  <li>Update game label status</li>
     *  <li>Bind game to game label</li>
     * </ol>
     */

    @PostMapping("/_cap/api/v1/labels")
    ResponseEntity<CreateLabelResponse> createLabel(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody CreateLabelRequest request
    );

    @GetMapping ("/_cap/api/v1/labels/{labelId}")
    ResponseEntity<GetLabelResponse> getLabelId(
            @PathVariable("labelId") String labelId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @DeleteMapping("/_cap/api/v1/labels/{labelId}")
    ResponseEntity<Void> deleteLabel(
            @PathVariable("labelId") String labelId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @PatchMapping("/_cap/api/v1/labels/{labelId}")
    ResponseEntity<Void> patchLabel(
            @PathVariable("labelId") String labelId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody PatchLabelRequest request
    );

    @PatchMapping("_cap/api/v1/labels/{labelId}/status")
    ResponseEntity<Void> patchStatusLabel(
            @PathVariable("labelId") String labelId,
            @RequestHeader("Authtorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody PatchStatusLabelRequest request
    );

    @PostMapping("/_cap/api/v1/labels/{labelId}/bind-game")
    ResponseEntity<Void> bindGameLabel(
            @PathVariable("labelId") String labelId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody BindGameLabelRequest request
    );

}