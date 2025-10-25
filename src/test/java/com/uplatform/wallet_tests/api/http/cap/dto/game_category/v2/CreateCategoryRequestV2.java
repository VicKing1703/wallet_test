package com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2;

import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import com.uplatform.wallet_tests.api.http.cap.dto.game_category.enums.CategoryTypeV2;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class CreateCategoryRequestV2 {
    private Map<LangEnum, String> names;
    private String alias;
    private Integer sort;
    private String projectId;
    private CategoryTypeV2 type;
    private String parentCategoryId;
}
