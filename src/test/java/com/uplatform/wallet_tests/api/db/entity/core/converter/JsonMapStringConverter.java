package com.uplatform.wallet_tests.api.db.entity.core.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

@Converter
public class JsonMapStringConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        try {
            return attribute == null ? null : MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize Map<String,String> to JSON", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        try {
            return dbData == null ? null : MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to Map<String,String>", e);
        }
    }
}