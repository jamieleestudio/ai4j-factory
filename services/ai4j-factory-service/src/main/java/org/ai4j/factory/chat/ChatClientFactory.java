package org.ai4j.factory.chat;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.client.okhttp.OkHttpClient;
import com.openai.core.ClientOptions;
import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ChatClientFactory {

    private final ModelCredentialRepository credentialRepository;

    public ChatClientFactory(ModelCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    public ChatClient create(Long credentialId, String modelName) {
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
                        .model(modelName != null && !modelName.isEmpty() ? modelName : "deepseek-chat")
                        .temperature(0.7)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }
}
