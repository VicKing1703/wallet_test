package com.uplatform.wallet_tests.api.nats.dto.enums;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NatsGamblingTransactionOperationConverter
        implements AttributeConverter<NatsGamblingTransactionOperation, String> {

    @Override
    public String convertToDatabaseColumn(NatsGamblingTransactionOperation attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public NatsGamblingTransactionOperation convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NatsGamblingTransactionOperation.valueOf(dbData.toUpperCase());
    }
}