package org.ai4j.factory.chat;

import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.entity.ModelProvider;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatClientFactoryTest {

    @Test
    void createBuildsClientWithoutDefaultAsyncCredentialFallback() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient client = factory.create(1L, "gpt-4o-mini");

        assertThat(client).isNotNull();
    }

    @Test
    void createReusesCachedClientForSameCredentialAndModel() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient first = factory.create(1L, "gpt-4o-mini");
        ChatClient second = factory.create(1L, "gpt-4o-mini");

        assertThat(second).isSameAs(first);
        verify(repository, times(1)).findById(1L);
    }

    @Test
    void createUsesDifferentCacheEntriesForDifferentModels() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient first = factory.create(1L, "gpt-4o-mini");
        ChatClient second = factory.create(1L, "gpt-4.1-mini");

        assertThat(second).isNotSameAs(first);
        verify(repository, times(2)).findById(1L);
    }

    @Test
    void createNormalizesBlankModelNameToDefaultCacheKey() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient first = factory.create(1L, null);
        ChatClient second = factory.create(1L, "");
        ChatClient third = factory.create(1L, "deepseek-chat");

        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);
        verify(repository, times(1)).findById(1L);
    }

    @Test
    void evictCredentialRemovesAllModelsForCredential() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient firstA = factory.create(1L, "gpt-4o-mini");
        ChatClient firstB = factory.create(1L, "gpt-4.1-mini");
        factory.evictCredential(1L);
        ChatClient afterEvictA = factory.create(1L, "gpt-4o-mini");
        ChatClient afterEvictB = factory.create(1L, "gpt-4.1-mini");

        assertThat(afterEvictA).isNotSameAs(firstA);
        assertThat(afterEvictB).isNotSameAs(firstB);
        verify(repository, times(4)).findById(1L);
    }

    private static ModelCredential credential(Long id, String apiKey) {
        ModelProvider provider = new ModelProvider();
        provider.setId(1L);
        provider.setBaseUrl("https://api.openai.com/v1");

        ModelCredential credential = new ModelCredential();
        credential.setId(id);
        credential.setProvider(provider);
        credential.setApiKey(apiKey);
        credential.setEnabled(true);
        credential.setStatus(ModelCredential.CredentialStatus.VALID);
        return credential;
    }
}
