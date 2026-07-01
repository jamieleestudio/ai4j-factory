package org.ai4j.factory.sse;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StatusEvent.class, name = "status"),
        @JsonSubTypes.Type(value = IntentEvent.class, name = "intent"),
        @JsonSubTypes.Type(value = ChunkEvent.class, name = "chunk"),
        @JsonSubTypes.Type(value = ResultEvent.class, name = "result"),
        @JsonSubTypes.Type(value = ClarificationEvent.class, name = "clarification"),
        @JsonSubTypes.Type(value = ErrorEvent.class, name = "error"),
        @JsonSubTypes.Type(value = DoneEvent.class, name = "done"),
})
public sealed interface SseEvent
        permits StatusEvent, IntentEvent, ChunkEvent, ResultEvent, ClarificationEvent, ErrorEvent, DoneEvent {
}
