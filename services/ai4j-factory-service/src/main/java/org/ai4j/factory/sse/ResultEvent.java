package org.ai4j.factory.sse;

import java.util.List;
import java.util.Map;

public record ResultEvent(
        String chartType,
        List<Map<String, Object>> data,
        int rowCount
) implements SseEvent {
}
