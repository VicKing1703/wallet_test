package com.uplatform.wallet_tests.api.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGame;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGameSession;
import com.uplatform.wallet_tests.api.db.entity.core.CoreWallet;
import com.uplatform.wallet_tests.api.db.entity.core.GameProvider;
import com.uplatform.wallet_tests.api.db.repository.core.CoreGameProviderRepository;
import com.uplatform.wallet_tests.api.db.repository.core.CoreGameRepository;
import com.uplatform.wallet_tests.api.db.repository.core.CoreGameSessionRepository;
import com.uplatform.wallet_tests.api.db.repository.core.CoreWalletRepository;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

@Component
@Slf4j
public class CoreDatabaseClient extends AbstractDatabaseClient {
    private final CoreGameSessionRepository coreGameSessionRepository;
    private final CoreGameRepository coreGameRepository;
    private final CoreWalletRepository coreWalletRepository;
    private final CoreGameProviderRepository coreGameProviderRepository;
    

    public CoreDatabaseClient(AllureAttachmentService attachmentService,
                              CoreGameSessionRepository coreGameSessionRepository,
                              CoreGameRepository coreGameRepository,
                              CoreWalletRepository coreWalletRepository,
                              CoreGameProviderRepository coreGameProviderRepository,
                              ObjectMapper objectMapper) {
        super(attachmentService, objectMapper);
        this.coreGameSessionRepository = coreGameSessionRepository;
        this.coreGameRepository = coreGameRepository;
        this.coreWalletRepository = coreWalletRepository;
        this.coreGameProviderRepository = coreGameProviderRepository;
    }

    public CoreGameSession findLatestGameSessionByPlayerUuidOrFail(String playerUuid) {
        String description = String.format("latest core game session for player UUID '%s'", playerUuid);
        String attachmentNamePrefix = String.format("Core Game Session [PlayerUUID: %s]", playerUuid);
        Supplier<Optional<CoreGameSession>> querySupplier = () ->
                Optional.ofNullable(coreGameSessionRepository.findByPlayerUuidOrderByStartedAtDesc(playerUuid));
        return awaitAndGetJsonOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public CoreGame findGameByIdOrFail(int gameId) {
        String description = String.format("core game record by ID '%d'", gameId);
        String attachmentNamePrefix = String.format("Core Game Record [ID: %d]", gameId);

        Supplier<Optional<CoreGame>> querySupplier = () ->
                coreGameRepository.findById(gameId);
        return awaitAndGetJsonOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public CoreWallet findWalletByIdOrFail(int walletId) {
        String description = String.format("core wallet record by ID '%d'", walletId);
        String attachmentNamePrefix = String.format("Core Wallet Record [ID: %d]", walletId);

        Supplier<Optional<CoreWallet>> querySupplier = () ->
                coreWalletRepository.findById(walletId);
        return awaitAndGetJsonOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public GameProvider findGameProviderByIdOrFail(int providerId) {
        String description = String.format("core game provider record by ID '%d'", providerId);
        String attachmentNamePrefix = String.format("Core GameProvider Record [ID: %d]", providerId);

        Supplier<Optional<GameProvider>> querySupplier = () ->
                coreGameProviderRepository.findById(providerId);

        return awaitAndGetJsonOrFail(description, attachmentNamePrefix, querySupplier);
    }

}
