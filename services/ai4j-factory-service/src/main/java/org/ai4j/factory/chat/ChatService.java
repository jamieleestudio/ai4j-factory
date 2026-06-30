package org.ai4j.factory.chat;

import org.ai4j.factory.sse.ChunkEvent;
import org.ai4j.factory.sse.DoneEvent;
import org.ai4j.factory.sse.ErrorEvent;
import org.ai4j.factory.sse.SseEventSerializer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private final ChatClientFactory chatClientFactory;

    public ChatService(ChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    public Flux<String> streamChat(Long credentialId, String message, String modelName) {
        var chatClient = chatClientFactory.create(credentialId, modelName);
        return chatClient.prompt().user(message).stream().content()
                .map(token -> SseEventSerializer.toJson(new ChunkEvent(token)))
                .concatWith(Flux.just(SseEventSerializer.toJson(new DoneEvent())))
                .onErrorResume(e -> Flux.just(
                        SseEventSerializer.toJson(new ErrorEvent(e.getMessage())),
                        SseEventSerializer.toJson(new DoneEvent())));
    }
}
