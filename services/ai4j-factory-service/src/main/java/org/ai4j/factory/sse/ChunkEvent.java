package org.ai4j.factory.sse;

public record ChunkEvent(String content) implements SseEvent {
}
