package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.CoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CoreCategoryRepository extends JpaRepository<CoreCategory, String> {
    Optional<CoreCategory> findByUuid(String uuid);
}