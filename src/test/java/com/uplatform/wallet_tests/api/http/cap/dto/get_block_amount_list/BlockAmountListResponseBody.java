package com.uplatform.wallet_tests.api.http.cap.dto.get_block_amount_list;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import lombok.Data;
import java.util.List;

@Data
public class BlockAmountListResponseBody {
    private List<BlockAmountListItem> items;
}

