package com.uplatform.wallet_tests.api.http.cap.dto.gameCategory;

import com.uplatform.wallet_tests.api.http.cap.dto.gameCategory.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Builder
@Data
public class PatchGameCategoryRequest {
    private Integer sort;
    private String alias;
    private CategoryType type;
    private Map<LangEnum, String> names;
}
