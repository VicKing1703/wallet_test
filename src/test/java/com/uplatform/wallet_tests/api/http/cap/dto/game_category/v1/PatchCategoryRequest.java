package com.uplatform.wallet_tests.api.http.cap.dto.game_category.v1;

import com.uplatform.wallet_tests.api.http.cap.dto.game_category.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Builder
@Data
public class PatchCategoryRequest {
    private Integer sort;
    private String alias;
    private CategoryType type;
    private Map<LangEnum, String> names;
}
