package org.ai4j.factory.shared.credential.service;

import org.ai4j.factory.chat.ChatClientFactory;
import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.entity.ModelProvider;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.ai4j.factory.shared.credential.repository.ModelProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModelCredentialService {

    private final ModelCredentialRepository credentialRepository;
    private final ModelProviderRepository providerRepository;
    private final ChatClientFactory chatClientFactory;

    public ModelCredentialService(ModelCredentialRepository credentialRepository,
                                  ModelProviderRepository providerRepository,
                                  ChatClientFactory chatClientFactory) {
        this.credentialRepository = credentialRepository;
        this.providerRepository = providerRepository;
        this.chatClientFactory = chatClientFactory;
    }

    public List<ModelCredential> getCredentialsByUserId(String userId) {
        return credentialRepository.findByUserId(userId);
    }

    @Transactional
    public ModelCredential addCredential(String userId, Long providerId, String apiKey) {
        ModelProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        ModelCredential credential = new ModelCredential();
        credential.setUserId(userId);
        credential.setProvider(provider);
        credential.setApiKey(apiKey);
        credential.setStatus(ModelCredential.CredentialStatus.VALID);

        return credentialRepository.save(credential);
    }

    public boolean validateCredential(Long credentialId) {
        return credentialRepository.existsById(credentialId);
    }

    @Transactional
    public void deleteCredential(Long id) {
        credentialRepository.deleteById(id);
        chatClientFactory.evictCredential(id);
    }

    @Transactional
    public ModelCredential updateCredential(Long id, String apiKey) {
        ModelCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        credential.setApiKey(apiKey);
        ModelCredential saved = credentialRepository.save(credential);
        chatClientFactory.evictCredential(id);
        return saved;
    }

    @Transactional
    public ModelCredential toggleCredentialStatus(Long id, boolean enabled) {
        ModelCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        credential.setEnabled(enabled);
        ModelCredential saved = credentialRepository.save(credential);
        chatClientFactory.evictCredential(id);
        return saved;
    }
}
