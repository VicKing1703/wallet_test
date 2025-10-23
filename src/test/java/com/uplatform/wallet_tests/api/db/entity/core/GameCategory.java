package com.uplatform.wallet_tests.api.db.entity.core;

import com.uplatform.wallet_tests.api.db.entity.core.converter.LocalizedNamesConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
public class GameCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String alias;

    @Column(name = "created_at", nullable = false)
    private Integer createdAt;

    @Column(name = "updated_at", nullable = false)
    private Integer updatedAt;

    @Column(nullable = false)
    private String uuid;

    @Column(name = "project_group_uuid")
    private String projectGroupUuid;

    @Column(name = "project_uuid")
    private String projectUuid;

    @Column(name = "status_id", nullable = false)
    private Short statusId;

    @Column(name = "entity_sort")
    private Integer entitySort;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "parent_uuid")
    private String parentUuid;

    @Convert(converter = LocalizedNamesConverter.class)
    @Column(name = "localized_names", columnDefinition = "json", nullable = false)
    private Map<String, String> localizedNames;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "cms", nullable = false)
    private boolean cms;

    public String getProjectGroupUuid() {
        return projectGroupUuid == null ? "" : projectGroupUuid;
    }
}
