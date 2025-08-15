package com.uplatform.wallet_tests.api.db.entity.core;

import com.uplatform.wallet_tests.api.db.entity.core.converter.JsonMapStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Entity
@Table(name = "game_category")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CoreCategory {

    @Id
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false)
    private String alias;

    @Column(name = "created_at", nullable = false)
    private Integer createdAt;

    @Column(name = "updated_at", nullable = false)
    private Integer updatedAt;

    @Column(nullable = false)
    private String uuid;

    @Column(name = "status_id", nullable = false)
    private Integer statusId;

    private Integer sort;

    @Column(name = "project_group_uuid")
    private String projectGroupUuid;

    @Column(name = "project_uuid")
    private String projectUuid;

    @Column(name = "is_default", nullable = false)
    private Integer isDefault;

    @Convert(converter = JsonMapStringConverter.class)
    @Column(name = "localized_names", nullable = false)
    private Map<String, String> localizedNames;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Integer cms;

}

/*
CREATE TABLE `game_category` (
  `id` int NOT NULL AUTO_INCREMENT,
  `alias` varchar(100) NOT NULL,
  `created_at` int NOT NULL,
  `updated_at` int NOT NULL,
  `uuid` char(36) NOT NULL DEFAULT '',
  `status_id` smallint NOT NULL,
  `sort` int unsigned DEFAULT '0',
  `project_group_uuid` char(36) DEFAULT NULL,
  `project_uuid` char(36) DEFAULT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `localized_names` json NOT NULL DEFAULT (_utf8mb3'{}'),
  `type` varchar(36) NOT NULL DEFAULT 'vertical',
  `cms` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `game_category_unique` (`project_uuid`,`alias`,`project_group_uuid`),
  KEY `fk_game_status_id_rel` (`status_id`),
  CONSTRAINT `fk_game_status_id_rel` FOREIGN KEY (`status_id`) REFERENCES `game_category_status` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=38868 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
 */