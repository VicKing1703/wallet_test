package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.CoreGameCategoriesGames;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CoreGameCategoriesGamesRepository extends JpaRepository<CoreGameCategoriesGames, String> {
    Optional<CoreGameCategoriesGames> findByCategoryUuid(String categoryUuid);
    Optional<CoreGameCategoriesGames> findByGameUuid(String gameUuid);
}
