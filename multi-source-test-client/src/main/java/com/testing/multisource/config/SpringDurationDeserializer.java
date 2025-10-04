package com.testing.multisource.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.boot.convert.DurationStyle;

import java.io.IOException;
import java.time.Duration;

class SpringDurationDeserializer extends JsonDeserializer<Duration> {

    @Override
    public Duration deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return Duration.ofMillis(parser.getLongValue());
        }
        if (token == JsonToken.VALUE_STRING) {
            String text = parser.getText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return DurationStyle.detectAndParse(text);
            } catch (IllegalArgumentException ex) {
                throw JsonMappingException.from(parser, "Failed to parse Duration: " + text, ex);
            }
        }
        return (Duration) context.handleUnexpectedToken(Duration.class, parser);
    }
}
