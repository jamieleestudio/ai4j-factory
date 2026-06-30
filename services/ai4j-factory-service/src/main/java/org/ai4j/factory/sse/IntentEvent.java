package org.ai4j.factory.sse;

import java.util.List;
import java.util.Map;

public record IntentEvent(
        String subject,
        List<String> metrics,
        List<String> dimensions,
        List<Map<String, Object>> filters
) implements SseEvent {
}
