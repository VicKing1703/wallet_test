package com.uplatform.wallet_tests.api.redis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.uplatform.wallet_tests.api.redis.model.enums.IFrameRecordType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletFullData(
        @JsonProperty("WalletUUID") String walletUUID,
        @JsonProperty("PlayerUUID") String playerUUID,
        @JsonProperty("PlayerBonusUUID") String playerBonusUUID,
        @JsonProperty("NodeUUID") String nodeUUID,
        @JsonProperty("Type") int type,
        @JsonProperty("Status") int status,
        @JsonProperty("Valid") boolean valid,
        @JsonProperty("IsGamblingActive") boolean isGamblingActive,
        @JsonProperty("IsBettingActive") boolean isBettingActive,
        @JsonProperty("Currency") String currency,
        @JsonProperty("Balance") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal balance,
        @JsonProperty("AvailableWithdrawalBalance") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal availableWithdrawalBalance,
        @JsonProperty("BalanceBefore") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal balanceBefore,
        @JsonProperty("CreatedAt") long createdAt,
        @JsonProperty("UpdatedAt") long updatedAt,
        @JsonProperty("BlockDate") long blockDate,
        @JsonProperty("SumSubBlockDate") long sumSubBlockDate,
        @JsonProperty("KYCVerificationUpdateTo") long kycVerificationUpdateTo,
        @JsonProperty("LastSeqNumber") int lastSeqNumber,
        @JsonProperty("Default") boolean isDefault,
        @JsonProperty("Main") boolean main,
        @JsonProperty("IsBlocked") boolean isBlocked,
        @JsonProperty("IsKYCUnverified") boolean isKYCUnverified,
        @JsonProperty("IsSumSubVerified") boolean isSumSubVerified,
        @JsonProperty("BonusInfo") BonusInfo bonusInfo,
        @JsonProperty("BonusTransferTransactions") Map<String, Object> bonusTransferTransactions,
        @JsonProperty("Limits") List<LimitData> limits,
        @JsonProperty("IFrameRecords") List<IFrameRecord> iFrameRecords,
        @JsonProperty("Gambling") Map<String, GamblingTransaction> gambling,
        @JsonProperty("Deposits") List<DepositData> deposits,
        @JsonProperty("BlockedAmounts") List<BlockedAmount> blockedAmounts
) {
    public WalletFullData() {
        this(null, null, null, null, 0, 0, false, false, false, null,
                null, null, null, 0L, 0L, 0L, 0L, 0L, 0, false, false,
                false, false, false, null, null, null, null, null, null, null);
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BonusInfo(
            @JsonProperty("BonusUUID") String bonusUUID,
            @JsonProperty("BonusCategory") String bonusCategory,
            @JsonProperty("PlayerBonusUUID") String playerBonusUUID,
            @JsonProperty("NodeUUID") String nodeUUID,
            @JsonProperty("Wager") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal wager,
            @JsonProperty("Threshold") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal threshold,
            @JsonProperty("TransferValue") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal transferValue,
            @JsonProperty("BetMin") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal betMin,
            @JsonProperty("BetMax") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal betMax,
            @JsonProperty("TransferType") int transferType,
            @JsonProperty("RealPercent") int realPercent,
            @JsonProperty("BonusPercent") int bonusPercent
    ) {
        public BonusInfo() {
            this(null, null, null, null, null, null, null, null, null, 0, 0, 0);
        }

        public String getBonusUUID() { return bonusUUID; }
        public String getBonusCategory() { return bonusCategory; }
        public String getPlayerBonusUUID() { return playerBonusUUID; }
        public String getNodeUUID() { return nodeUUID; }
        public BigDecimal getWager() { return wager; }
        public BigDecimal getThreshold() { return threshold; }
        public BigDecimal getTransferValue() { return transferValue; }
        public BigDecimal getBetMin() { return betMin; }
        public BigDecimal getBetMax() { return betMax; }
        public int getTransferType() { return transferType; }
        public int getRealPercent() { return realPercent; }
        public int getBonusPercent() { return bonusPercent; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LimitData(
            @JsonProperty("ExternalID") String externalID,
            @JsonProperty("LimitType") String limitType,
            @JsonProperty("IntervalType") String intervalType,
            @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal amount,
            @JsonProperty("Spent") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal spent,
            @JsonProperty("Rest") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal rest,
            @JsonProperty("CurrencyCode") String currencyCode,
            @JsonProperty("StartedAt") Long startedAt,
            @JsonProperty("ExpiresAt") Long expiresAt,
            @JsonProperty("Status") boolean status
    ) {
        public LimitData() {
            this(null, null, null, null, null, null, null, null, null, false);
        }

        public String getExternalID() { return externalID; }
        public String getLimitType() { return limitType; }
        public String getIntervalType() { return intervalType; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getSpent() { return spent; }
        public BigDecimal getRest() { return rest; }
        public String getCurrencyCode() { return currencyCode; }
        public Long getStartedAt() { return startedAt; }
        public Long getExpiresAt() { return expiresAt; }
        public boolean isStatus() { return status; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IFrameRecord(
            @JsonProperty("UUID") String uuid,
            @JsonProperty("BetID") long betID,
            @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal amount,
            @JsonProperty("TotalCoeff") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal totalCoeff,
            @JsonProperty("Time") Long time,
            @JsonProperty("CreatedAt") Long createdAt,
            @JsonProperty("Type") IFrameRecordType type
    ) {
        public IFrameRecord() {
            this(null, 0L, null, null, null, null, null);
        }

        public String getUuid() { return uuid; }
        public long getBetID() { return betID; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getTotalCoeff() { return totalCoeff; }
        public Long getTime() { return time; }
        public Long getCreatedAt() { return createdAt; }
        public IFrameRecordType getType() { return type; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GamblingTransaction(
            @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal amount,
            @JsonProperty("CreatedAt") Long createdAt
    ) {
        public GamblingTransaction() {
            this(null, null);
        }

        public BigDecimal getAmount() { return amount; }
        public Long getCreatedAt() { return createdAt; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DepositData(
            @JsonProperty("UUID") String uuid,
            @JsonProperty("NodeUUID") String nodeUUID,
            @JsonProperty("BonusID") String bonusID,
            @JsonProperty("CurrencyCode") String currencyCode,
            @JsonProperty("Status") int status,
            @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal amount,
            @JsonProperty("WageringAmount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal wageringAmount
    ) {
        public DepositData() {
            this(null, null, null, null, 0, null, null);
        }

        public String getUuid() { return uuid; }
        public String getNodeUUID() { return nodeUUID; }
        public String getBonusID() { return bonusID; }
        public String getCurrencyCode() { return currencyCode; }
        public int getStatus() { return status; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getWageringAmount() { return wageringAmount; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlockedAmount(
            @JsonProperty("UUID") String uuid,
            @JsonProperty("UserUUID") String userUUID,
            @JsonProperty("Type") int type,
            @JsonProperty("Status") int status,
            @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal amount,
            @JsonProperty("DeltaAvailableWithdrawalBalance") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) BigDecimal deltaAvailableWithdrawalBalance,
            @JsonProperty("Reason") String reason,
            @JsonProperty("UserName") String userName,
            @JsonProperty("CreatedAt") Long createdAt,
            @JsonProperty("ExpiredAt") Long expiredAt
    ) {
        public BlockedAmount() {
            this(null, null, 0, 0, null, null, null, null, null, null);
        }

        public String getUuid() { return uuid; }
        public String getUserUUID() { return userUUID; }
        public int getType() { return type; }
        public int getStatus() { return status; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getDeltaAvailableWithdrawalBalance() { return deltaAvailableWithdrawalBalance; }
        public String getReason() { return reason; }
        public String getUserName() { return userName; }
        public Long getCreatedAt() { return createdAt; }
        public Long getExpiredAt() { return expiredAt; }
    }
}

