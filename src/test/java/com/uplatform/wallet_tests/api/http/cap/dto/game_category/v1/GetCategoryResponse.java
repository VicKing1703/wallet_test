package com.uplatform.wallet_tests.api.http.cap.dto.game_category.v1;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class GetCategoryResponse {
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
