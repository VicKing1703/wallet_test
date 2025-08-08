package com.uplatform.wallet_tests.api.http.cap.dto.brand;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class GetBrandResponse {
    private String id;
    private Map<String, String> names;
    private String alias;
    private List<String> gameIds;
    private Integer status;
    private Integer sort;
    private String nodeId;
    private Integer createdAt;
    private Integer updatedAt;
    private String createdBy;
    private String updatedBy;
    private String icon;
    private String logo;
    private String colorLogo;
}
