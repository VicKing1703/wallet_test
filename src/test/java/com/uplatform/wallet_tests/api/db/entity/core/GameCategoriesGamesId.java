package com.uplatform.wallet_tests.api.db.entity.core;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameCategoriesGamesId implements Serializable {

    @Column(name = "game_id", nullable = false)
    private Integer gameId;

    @Column(name = "game_category_id", nullable = false)
    private Integer gameCategoryId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameCategoriesGamesId that)) return false;
        return Objects.equals(gameId, that.gameId) &&
                Objects.equals(gameCategoryId, that.gameCategoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameId, gameCategoryId);
    }
}
