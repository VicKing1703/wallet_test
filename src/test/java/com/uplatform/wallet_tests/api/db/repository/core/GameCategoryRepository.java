package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.GameCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameCategoryRepository extends JpaRepository<GameCategory, Integer> {
    Optional<GameCategory> findByUuid(String uuid);

    long countByUuid(String uuid);
}
