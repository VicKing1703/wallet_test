package com.uplatform.wallet_tests.api.http.cap.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalizedName {

    private String ru;
    private String en;
    private String lv;
}
