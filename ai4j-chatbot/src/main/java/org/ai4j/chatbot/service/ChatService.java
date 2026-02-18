package org.ai4j.chatbot.service;

import org.ai4j.chatbot.entity.ModelCredential;
import org.ai4j.chatbot.repository.ModelCredentialRepository;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private final ModelCredentialRepository credentialRepository;

    public ChatService(ModelCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    public Flux<ChatResponse> streamChat(Long credentialId, String message, String modelName) {
        ModelCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new RuntimeException("Credential not found with id: " + credentialId));

        if (credential.getStatus() != ModelCredential.CredentialStatus.VALID || !credential.isEnabled()) {
            throw new RuntimeException("Credential is not valid or disabled");
        }

        String baseUrl = credential.getProvider().getBaseUrl();
        String apiKey = credential.getApiKey();

        // Construct OpenAiApi dynamically
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        // Construct ChatModel dynamically
        // Note: We might want to cache these models if they are reused frequently,
        // but for now, we create a new one for each request to support dynamic configuration.
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelName != null && !modelName.isEmpty() ? modelName : "deepseek-chat") // Default fallback
                        .build())
                .build();

        return chatModel.stream(new Prompt(new UserMessage(message)));
    }
}
