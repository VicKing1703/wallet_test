package com.uplatform.wallet_tests.api.http.cap.dto.gameCategory;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DeleteGameCategoryRequest {
    private String id;
}
