package com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DeleteCategoryRequestV2 {
    private String id;
}
