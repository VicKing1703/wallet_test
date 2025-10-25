package com.uplatform.wallet_tests.api.http.cap.dto.game_category.v1;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PatchCategoryStatusRequest {
    private Integer status;
}
