package com.uplatform.wallet_tests.api.http.cap.dto.game_category.v2;

import com.uplatform.wallet_tests.api.http.cap.dto.enums.LangEnum;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Builder
@Data
public class PatchCategoryRequestV2 {
    private String alias;
    private Integer sort;
    private Boolean passToCms;
    private List<String> gameIds;
    private Map<LangEnum, String> names;
}
