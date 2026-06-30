package org.ai4j.factory.sse;

public record ErrorEvent(String message) implements SseEvent {
}
