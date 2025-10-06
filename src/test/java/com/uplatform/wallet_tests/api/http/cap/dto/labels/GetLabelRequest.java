package com.uplatform.wallet_tests.api.http.cap.dto.labels;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class GetLabelRequest {
    private String id;
}
