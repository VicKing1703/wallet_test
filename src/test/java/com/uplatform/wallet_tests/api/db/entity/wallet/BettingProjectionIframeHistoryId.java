package com.uplatform.wallet_tests.api.db.entity.wallet;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BettingProjectionIframeHistoryId implements Serializable {
    private String uuid;
    private Long seq;
}