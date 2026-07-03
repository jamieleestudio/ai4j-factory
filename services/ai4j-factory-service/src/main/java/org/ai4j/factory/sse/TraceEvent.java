package org.ai4j.factory.sse;

import java.util.Map;

public record TraceEvent(
        String spanId,
        String parentId,
        String name,
        TraceStatus status,
        Map<String, Object> attributes
) implements SseEvent {
}
