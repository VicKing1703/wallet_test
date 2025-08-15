package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.CoreGameCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CoreGameCategoryRepository extends JpaRepository<CoreGameCategory, String> {
    Optional<CoreGameCategory> findByUuid(String uuid);
}