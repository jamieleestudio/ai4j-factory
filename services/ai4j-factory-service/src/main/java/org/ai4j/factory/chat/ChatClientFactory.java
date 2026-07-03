package org.ai4j.factory.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.client.okhttp.OkHttpClient;
import com.openai.core.ClientOptions;
import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Component
public class ChatClientFactory {

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final ModelCredentialRepository credentialRepository;
    private final Cache<ChatClientCacheKey, ChatClient> cache;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public ChatClientFactory(ModelCredentialRepository credentialRepository, ChatMemory chatMemory) {
        this.credentialRepository = credentialRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Transactional
    public ChatClient create(Long credentialId, String modelName) {
        String normalizedModelName = normalizeModelName(modelName);
        return cache.get(new ChatClientCacheKey(credentialId, normalizedModelName),
                key -> buildChatClient(key.credentialId(), key.modelName()));
    }

    public void evict(Long credentialId, String modelName) {
        cache.invalidate(new ChatClientCacheKey(credentialId, normalizeModelName(modelName)));
    }

    public void evictCredential(Long credentialId) {
        cache.asMap().keySet().removeIf(key -> key.credentialId().equals(credentialId));
    }

    private ChatClient buildChatClient(Long credentialId, String modelName) {
        ModelCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new RuntimeException("Credential not found with id: " + credentialId));

        if (credential.getStatus() != ModelCredential.CredentialStatus.VALID || !credential.isEnabled()) {
            throw new RuntimeException("Credential is not valid or disabled");
        }

        String baseUrl = credential.getProvider().getBaseUrl();
        String apiKey = credential.getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("API key is empty for credential: " + credentialId);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new RuntimeException("Base URL is empty for provider: " + credential.getProvider().getId());
        }

        ClientOptions clientOptions = ClientOptions.builder()
                .httpClient(OkHttpClient.builder().build())
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(2)
                .build();

        OpenAIClient openAiClient = new OpenAIClientImpl(clientOptions);
        OpenAIClientAsync openAiClientAsync = new OpenAIClientAsyncImpl(clientOptions);

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .openAiClientAsync(openAiClientAsync)
                .options(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.7)
                        .streamUsage(true)
                        .build())
                .build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    private String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return DEFAULT_MODEL;
        }
        return modelName;
    }

    private record ChatClientCacheKey(Long credentialId, String modelName) {
    }
}
