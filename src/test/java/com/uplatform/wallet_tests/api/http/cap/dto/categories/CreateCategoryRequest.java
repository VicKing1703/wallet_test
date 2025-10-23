package com.uplatform.wallet_tests.api.http.cap.dto.categories;

import com.uplatform.wallet_tests.api.http.cap.dto.LocalizedName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateCategoryRequest {

    private LocalizedName names;
    private String alias;
    private Integer sort;
    private String projectId;
    private CategoryType type;
}
