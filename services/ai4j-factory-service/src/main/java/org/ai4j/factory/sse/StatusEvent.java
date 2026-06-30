package org.ai4j.factory.sse;

public record StatusEvent(String stage, String message) implements SseEvent {
}
