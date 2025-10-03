package com.uplatform.wallet_tests.api.db.repository.player;

import com.uplatform.wallet_tests.api.db.entity.player.AccountProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountPropertyRepository extends JpaRepository<AccountProperty, Integer> {
    @Query(value = "SELECT p.name_value AS nameValue, ap.status_value AS statusValue " +
            "FROM account_property ap " +
            "JOIN account a ON a.id = ap.account_id " +
            "JOIN property p ON ap.property_id = p.id " +
            "WHERE a.external_id_value = :playerUuid", nativeQuery = true)
    List<AccountPropertyStatusProjection> findStatusesByPlayerUuid(@Param("playerUuid") String playerUuid);
}
