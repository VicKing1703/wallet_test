package com.uplatform.wallet_tests.api.http.cap.dto.category;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class GetGameCategoryResponse {
    private String id;
    private String name;
    private String alias;
    private String projectId;
    private String groupId;
    private String type;
    private String passToCms;
    private String gamesCount;
    private List<String> gameIds;
    private String status;
    private String sort;
    private String isDefault;
    private Map<String, String> names;
}
