package com.uplatform.wallet_tests.api.db.entity.player;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_property")
@Data
@NoArgsConstructor
public class AccountProperty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "account_id")
    private Integer accountId;

    @Column(name = "property_id")
    private Integer propertyId;

    @Column(name = "status_value", nullable = false)
    private Boolean statusValue;
}
