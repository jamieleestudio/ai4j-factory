package org.ai4j.factory.sse;

import java.util.List;
import java.util.Map;

public record IntentEvent(
        String subject,
        List<String> metrics,
        List<DimensionRef> dimensions,
        List<Map<String, Object>> filters
) implements SseEvent {
}
