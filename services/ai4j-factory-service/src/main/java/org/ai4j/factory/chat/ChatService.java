package org.ai4j.factory.chat;

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
        return chatClient.prompt().user(message).stream().content();
    }
}
