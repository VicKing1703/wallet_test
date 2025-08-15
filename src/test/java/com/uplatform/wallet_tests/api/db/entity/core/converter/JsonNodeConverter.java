package com.uplatform.wallet_tests.api.db.entity.core.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        try {
            return attribute == null ? null : MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize JsonNode to JSON", e);
        }
    }

    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        try {
            return dbData == null ? null : MAPPER.readTree(dbData);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to JsonNode", e);
        }
    }
}