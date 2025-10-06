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
public class CoreGameCategory {

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
