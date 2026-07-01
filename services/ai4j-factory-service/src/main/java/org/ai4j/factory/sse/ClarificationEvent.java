package org.ai4j.factory.sse;

import java.util.List;

public record ClarificationEvent(
        String sessionId,
        String message,
        List<ClarificationOption> options
) implements SseEvent {
}
