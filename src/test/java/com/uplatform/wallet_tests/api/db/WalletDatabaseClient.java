package com.uplatform.wallet_tests.api.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.db.entity.wallet.*;
import com.uplatform.wallet_tests.api.db.repository.wallet.*;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

@Component
@Slf4j
public class WalletDatabaseClient extends AbstractDatabaseClient {

    private final GamblingProjectionTransactionHistoryRepository transactionRepository;
    private final PlayerThresholdWinRepository playerThresholdWinRepository;
    private final PlayerThresholdDepositRepository playerThresholdDepositRepository;
    private final WalletGameSessionRepository walletGameSessionRepository;
    private final WalletRepository walletRepository;
    private final BettingProjectionIframeHistoryRepository iframeHistoryRepository;

    public WalletDatabaseClient(AllureAttachmentService attachmentService,
                                GamblingProjectionTransactionHistoryRepository transactionRepository,
                                PlayerThresholdWinRepository playerThresholdWinRepository,
                                PlayerThresholdDepositRepository playerThresholdDepositRepository,
                                WalletGameSessionRepository walletGameSessionRepository,
                                WalletRepository walletRepository,
                                BettingProjectionIframeHistoryRepository iframeHistoryRepository,
                                ObjectMapper objectMapper) {
        super(attachmentService);
        this.transactionRepository = transactionRepository;
        this.playerThresholdWinRepository = playerThresholdWinRepository;
        this.playerThresholdDepositRepository = playerThresholdDepositRepository;
        this.walletGameSessionRepository = walletGameSessionRepository;
        this.walletRepository = walletRepository;
        this.iframeHistoryRepository = iframeHistoryRepository;
    }

    @Transactional(readOnly = true)
    public GamblingProjectionTransactionHistory findTransactionByUuidOrFail(String uuid) {
        String description = String.format("transaction history record by UUID '%s'", uuid);
        String attachmentNamePrefix = String.format("Wallet Transaction Record [UUID: %s]", uuid);
        Supplier<Optional<GamblingProjectionTransactionHistory>> querySupplier = () ->
                transactionRepository.findById(uuid);
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public PlayerThresholdWin findThresholdByPlayerUuidOrFail(String playerUuid) {
        String description = String.format("player threshold win record for player '%s'", playerUuid);
        String attachmentNamePrefix = String.format("Player Threshold Win [Player: %s]", playerUuid);
        Supplier<Optional<PlayerThresholdWin>> querySupplier = () ->
                playerThresholdWinRepository.findByPlayerUuid(playerUuid);
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public PlayerThresholdDeposit findDepositThresholdByPlayerUuidOrFail(String playerUuid) {
        String description = String.format("player threshold deposit record for player '%s'", playerUuid);
        String attachmentNamePrefix = String.format("Player Threshold Deposit [Player: %s]", playerUuid);
        Supplier<Optional<PlayerThresholdDeposit>> querySupplier = () ->
                playerThresholdDepositRepository.findByPlayerUuid(playerUuid);
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public WalletGameSession findSingleGameSessionByPlayerUuidOrFail(String playerUuid) {
        String description = String.format("single game session for player UUID '%s'", playerUuid);
        String attachmentNamePrefix = String.format("Wallet Game Session [PlayerUUID: %s]", playerUuid);
        Supplier<Optional<WalletGameSession>> querySupplier = () ->
                walletGameSessionRepository.findByPlayerUuid(playerUuid);
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public Wallet findWalletByUuidOrFail(String walletUuid) {
        String description = String.format("wallet record by UUID '%s'", walletUuid);
        String attachmentPrefix = String.format("Wallet Record [UUID: %s]", walletUuid);
        Supplier<Optional<Wallet>> querySupplier = () ->
                walletRepository.findByUuid(walletUuid);
        return awaitAndGetOrFail(description, attachmentPrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public BettingProjectionIframeHistory findLatestIframeHistoryByUuidOrFail(String uuid) {
        String description = String.format("latest betting iframe history record by UUID '%s'", uuid);
        String attachmentNamePrefix = String.format("Betting Iframe History [UUID: %s, Latest]", uuid);

        Supplier<Optional<BettingProjectionIframeHistory>> querySupplier = () ->
                iframeHistoryRepository.findFirstByUuidOrderBySeqDesc(uuid);

        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

}
