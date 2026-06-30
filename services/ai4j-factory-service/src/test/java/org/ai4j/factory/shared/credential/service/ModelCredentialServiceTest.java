package org.ai4j.factory.shared.credential.service;

import org.ai4j.factory.chat.ChatClientFactory;
import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.ai4j.factory.shared.credential.repository.ModelProviderRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelCredentialServiceTest {

    @Test
    void updateCredentialEvictsCachedChatClients() {
        ModelCredentialRepository credentialRepository = mock(ModelCredentialRepository.class);
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        ModelCredential credential = credential(1L);
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(credential)).thenReturn(credential);

        ModelCredentialService service = new ModelCredentialService(
                credentialRepository, providerRepository, chatClientFactory);

        service.updateCredential(1L, "new-key");

        verify(chatClientFactory).evictCredential(1L);
    }

    @Test
    void deleteCredentialEvictsCachedChatClients() {
        ModelCredentialRepository credentialRepository = mock(ModelCredentialRepository.class);
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        ModelCredentialService service = new ModelCredentialService(
                credentialRepository, providerRepository, chatClientFactory);

        service.deleteCredential(1L);

        verify(chatClientFactory).evictCredential(1L);
    }

    @Test
    void toggleCredentialStatusEvictsCachedChatClients() {
        ModelCredentialRepository credentialRepository = mock(ModelCredentialRepository.class);
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        ModelCredential credential = credential(1L);
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(credential)).thenReturn(credential);

        ModelCredentialService service = new ModelCredentialService(
                credentialRepository, providerRepository, chatClientFactory);

        service.toggleCredentialStatus(1L, false);

        verify(chatClientFactory).evictCredential(1L);
    }

    private static ModelCredential credential(Long id) {
        ModelCredential credential = new ModelCredential();
        credential.setId(id);
        credential.setApiKey("old-key");
        credential.setEnabled(true);
        credential.setStatus(ModelCredential.CredentialStatus.VALID);
        return credential;
    }
}
