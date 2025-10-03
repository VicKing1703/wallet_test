package com.uplatform.wallet_tests.api.db.repository.wallet;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.uplatform.wallet_tests.api.db.entity.wallet.WalletGameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WalletGameSessionRepository extends JpaRepository<WalletGameSession, String> {
    Optional<WalletGameSession> findByPlayerUuid(String playerUuid);
}