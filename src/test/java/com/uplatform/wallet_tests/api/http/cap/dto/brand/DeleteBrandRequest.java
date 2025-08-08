package com.uplatform.wallet_tests.api.http.cap.dto.brand;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DeleteBrandRequest {
    private String id;
}