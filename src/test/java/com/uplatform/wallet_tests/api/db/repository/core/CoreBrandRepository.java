package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.CoreBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CoreBrandRepository extends JpaRepository<CoreBrand, String> {
    Optional<CoreBrand> findByUuid(String uuid);
}