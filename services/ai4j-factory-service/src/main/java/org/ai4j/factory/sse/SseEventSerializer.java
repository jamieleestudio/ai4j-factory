package org.ai4j.factory.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SseEventSerializer {

    private static final Logger log = LoggerFactory.getLogger(SseEventSerializer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseEventSerializer() {
    }

    public static String toJson(SseEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize SSE event: {}", e.getMessage());
            return "{\"type\":\"error\",\"message\":\"serialization failed\"}";
        }
    }
}
