package com.uplatform.wallet_tests.api.db.entity.core;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_categories_games")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CoreGameCategoriesGames {

    @EmbeddedId
    private GameCategoriesGamesId id;

    @Column(name = "created_at", nullable = false)
    private Integer createdAt;

    @Column(name = "updated_at", nullable = false)
    private Integer updatedAt;

    @Column(nullable = false)
    private Integer sort;

    @Column(name = "category_uuid", length = 36, nullable = false)
    private String categoryUuid;

    @Column(name = "game_uuid", length = 36, nullable = false)
    private String gameUuid;
}
