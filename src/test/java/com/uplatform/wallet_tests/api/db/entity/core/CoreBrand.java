package com.uplatform.wallet_tests.api.db.entity.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.uplatform.wallet_tests.api.db.entity.core.converter.JsonMapStringConverter;
import com.uplatform.wallet_tests.api.db.entity.core.converter.JsonNodeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Entity
@Table(name = "brand")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CoreBrand {

    @Id
    @Column(nullable = false)
    private String uuid;

    @Convert(converter = JsonMapStringConverter.class)
    @Column(name = "localized_names", nullable = false)
    private Map<String, String> localizedNames;

    @Column(nullable = false)
    private String alias;

    @Column(nullable = false)
    private String description;

    @Column(name = "node_uuid", nullable = false)
    private String nodeUuid;

    @Column(nullable = false)
    private Integer status;

    @Column(nullable = false)
    private Integer sort;

    @Column(name = "created_at", nullable = false)
    private Integer createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private Integer updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted_at")
    private Integer deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "alias_for_index")
    private String aliasForIndex;

    @Convert(converter = JsonNodeConverter.class)
    private JsonNode icon;

    @Convert(converter = JsonNodeConverter.class)
    private JsonNode logo;
}
