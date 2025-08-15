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

/*
CREATE TABLE `brand` (
        `uuid` varchar(36) NOT NULL,
  `localized_names` json NOT NULL,
        `alias` varchar(100) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `node_uuid` varchar(36) NOT NULL,
  `status` smallint NOT NULL DEFAULT '2',
        `sort` int NOT NULL DEFAULT '0',
        `created_at` int NOT NULL,
        `created_by` varchar(100) NOT NULL,
  `updated_at` int DEFAULT NULL,
        `updated_by` varchar(100) DEFAULT NULL,
  `deleted_at` int DEFAULT NULL,
        `deleted_by` varchar(100) DEFAULT NULL,
  `alias_for_index` varchar(100) GENERATED ALWAYS AS (if(((`deleted_at` is null) or (`deleted_at` = 0)),`alias`,NULL)) STORED,
        `icon` json DEFAULT NULL,
        `logo` json DEFAULT NULL,
PRIMARY KEY (`uuid`),
UNIQUE KEY `brand_alias_uniq_node` (`alias_for_index`,`node_uuid`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
 */