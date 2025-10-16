package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.CoreGame;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoreGameRepository extends JpaRepository<CoreGame, Integer> {
    Optional<CoreGame> findByUuid(String uuid);
    Optional<CoreGame> findByAlias(String alias);

    @Query(value = "SELECT uuid FROM game WHERE deleted_at IS NULL ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<String> findRandomUuids(@Param("limit") int limit);
}