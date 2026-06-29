package org.ai4j.factory.chat;

import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.entity.ModelProvider;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatClientFactoryTest {

    @Test
    void createDoesNotBuildDefaultClientWithoutCredentials() {
        ModelProvider provider = new ModelProvider();
        provider.setId(1L);
        provider.setBaseUrl("https://api.openai.com/v1");

        ModelCredential credential = new ModelCredential();
        credential.setId(1L);
        credential.setProvider(provider);
        credential.setApiKey("test-api-key");
        credential.setEnabled(true);
        credential.setStatus(ModelCredential.CredentialStatus.VALID);

        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential));

        ChatClientFactory factory = new ChatClientFactory(repository);

        assertThatNoException().isThrownBy(() -> factory.create(1L, "gpt-4o-mini"));
    }
}
