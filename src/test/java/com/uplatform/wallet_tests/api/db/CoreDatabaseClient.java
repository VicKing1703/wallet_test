package com.uplatform.wallet_tests.api.db;

import com.testing.multisource.api.attachment.AttachmentType;
import com.testing.multisource.api.attachment.AllureAttachmentService;
import com.testing.multisource.api.db.AbstractDatabaseClient;
import com.testing.multisource.api.db.exceptions.DatabaseQueryTimeoutException;
import com.testing.multisource.api.db.exceptions.DatabaseRecordNotFoundException;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGame;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGameSession;
import com.uplatform.wallet_tests.api.db.entity.core.CoreWallet;
import com.uplatform.wallet_tests.api.db.entity.core.GameCategory;
import com.uplatform.wallet_tests.api.db.entity.core.GameProvider;
import com.uplatform.wallet_tests.api.db.repository.core.CoreGameProviderRepository;
import com.uplatform.wallet_tests.api.db.repository.core.CoreGameRepository;
import com.uplatform.wallet_tests.api.db.repository.core.CoreGameSessionRepository;
import com.uplatform.wallet_tests.api.db.repository.core.CoreWalletRepository;
import com.uplatform.wallet_tests.api.db.repository.core.GameCategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;

@Component
@Slf4j
public class CoreDatabaseClient extends AbstractDatabaseClient {
    private final CoreGameSessionRepository coreGameSessionRepository;
    private final CoreGameRepository coreGameRepository;
    private final CoreWalletRepository coreWalletRepository;
    private final CoreGameProviderRepository coreGameProviderRepository;
    private final GameCategoryRepository gameCategoryRepository;
    

    public CoreDatabaseClient(AllureAttachmentService attachmentService,
                              CoreGameSessionRepository coreGameSessionRepository,
                              CoreGameRepository coreGameRepository,
                              CoreWalletRepository coreWalletRepository,
                              CoreGameProviderRepository coreGameProviderRepository,
                              GameCategoryRepository gameCategoryRepository) {
        super(attachmentService);
        this.coreGameSessionRepository = coreGameSessionRepository;
        this.coreGameRepository = coreGameRepository;
        this.coreWalletRepository = coreWalletRepository;
        this.coreGameProviderRepository = coreGameProviderRepository;
        this.gameCategoryRepository = gameCategoryRepository;
    }

    public CoreGameSession findLatestGameSessionByPlayerUuidOrFail(String playerUuid) {
        String description = String.format("latest core game session for player UUID '%s'", playerUuid);
        String attachmentNamePrefix = String.format("Core Game Session [PlayerUUID: %s]", playerUuid);
        Supplier<Optional<CoreGameSession>> querySupplier = () ->
                Optional.ofNullable(coreGameSessionRepository.findByPlayerUuidOrderByStartedAtDesc(playerUuid));
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public CoreGame findGameByIdOrFail(int gameId) {
        String description = String.format("core game record by ID '%d'", gameId);
        String attachmentNamePrefix = String.format("Core Game Record [ID: %d]", gameId);

        Supplier<Optional<CoreGame>> querySupplier = () ->
                coreGameRepository.findById(gameId);
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public CoreWallet findWalletByIdOrFail(int walletId) {
        String description = String.format("core wallet record by ID '%d'", walletId);
        String attachmentNamePrefix = String.format("Core Wallet Record [ID: %d]", walletId);

        Supplier<Optional<CoreWallet>> querySupplier = () ->
                coreWalletRepository.findById(walletId);
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public GameProvider findGameProviderByIdOrFail(int providerId) {
        String description = String.format("core game provider record by ID '%d'", providerId);
        String attachmentNamePrefix = String.format("Core GameProvider Record [ID: %d]", providerId);

        Supplier<Optional<GameProvider>> querySupplier = () ->
                coreGameProviderRepository.findById(providerId);

        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public GameCategory findGameCategoryByUuidOrFail(String uuid) {
        String description = String.format("game category record by UUID '%s'", uuid);
        String attachmentNamePrefix = String.format("Game Category Record [UUID: %s]", uuid);

        Supplier<Optional<GameCategory>> querySupplier = () ->
                gameCategoryRepository.findByUuid(uuid);

        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public long waitForGameCategoryDeletionOrFail(String uuid) {
        String description = String.format("absence of game category record by UUID '%s'", uuid);
        String attachmentNamePrefix = String.format("Game Category Record [UUID: %s]", uuid);

        try {
            ConditionFactory condition = await(description)
                    .atMost(retryTimeoutDuration)
                    .pollInterval(retryPollIntervalDuration)
                    .pollDelay(retryPollDelayDuration)
                    .ignoreExceptionsInstanceOf(TransientDataAccessException.class);

            condition.until(() -> gameCategoryRepository.countByUuid(uuid) == 0);

            attachmentService.attachText(AttachmentType.DB,
                    attachmentNamePrefix + " - Deleted",
                    "Record successfully removed. Remaining rows: 0");

            return 0L;
        } catch (ConditionTimeoutException e) {
            long actualCount = gameCategoryRepository.countByUuid(uuid);
            attachmentService.attachText(AttachmentType.DB,
                    attachmentNamePrefix + " - Still Exists",
                    String.format("Expected 0 rows but found %d after waiting %s", actualCount, retryTimeoutDuration));
            throw new DatabaseRecordNotFoundException(
                    String.format("Game category with UUID '%s' still exists in DB (found %d rows)", uuid, actualCount), e);
        } catch (Exception e) {
            attachmentService.attachText(AttachmentType.DB,
                    attachmentNamePrefix + " - Error",
                    "Error type: " + e.getClass().getName() + "\nMessage: " + e.getMessage());
            throw new DatabaseQueryTimeoutException(
                    String.format("Unexpected error while waiting for deletion of game category '%s'", uuid), e);
        }
    }
}
