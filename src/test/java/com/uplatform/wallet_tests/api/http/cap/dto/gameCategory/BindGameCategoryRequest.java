package com.uplatform.wallet_tests.api.http.cap.dto.gameCategory;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Builder
@Data
public class BindGameCategoryRequest {
    private List<String> gameIds;
}
