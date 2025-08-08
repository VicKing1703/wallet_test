package com.uplatform.wallet_tests.api.http.cap.dto.brand;

import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.CategoryType;
import com.uplatform.wallet_tests.api.http.cap.dto.category.enums.LangEnum;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class CreateBrandRequest {
    private Integer sort;
    private String alias;
    private Map<LangEnum, String> names;
    private String description;
}

