package org.ai4j.factory.chat;

import org.ai4j.factory.sse.ChunkEvent;
import org.ai4j.factory.sse.DoneEvent;
import org.ai4j.factory.sse.ErrorEvent;
import org.ai4j.factory.sse.SseEventSerializer;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private final ChatClientFactory chatClientFactory;

    public ChatService(ChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    public Flux<ServerSentEvent<String>> streamChat(Long credentialId, String message, String modelName, String sessionId) {
        var chatClient = chatClientFactory.create(credentialId, modelName);
        return chatClient.prompt().user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream().content()
                .map(token -> SseEventSerializer.toServerSentEvent(new ChunkEvent(token)))
                .concatWith(Flux.just(SseEventSerializer.toServerSentEvent(new DoneEvent())))
                .onErrorResume(e -> Flux.just(
                        SseEventSerializer.toServerSentEvent(new ErrorEvent(e.getMessage())),
                        SseEventSerializer.toServerSentEvent(new DoneEvent())));
    }
}
