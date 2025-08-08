package com.uplatform.wallet_tests.api.http.cap.dto.category;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class GetCategoryIdRequest {
    private String categoryId;
}
