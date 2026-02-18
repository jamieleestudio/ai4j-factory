package org.ai4j.chatbot.service;

import org.ai4j.chatbot.entity.ModelCredential;
import org.ai4j.chatbot.entity.ModelProvider;
import org.ai4j.chatbot.repository.ModelCredentialRepository;
import org.ai4j.chatbot.repository.ModelProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModelCredentialService {

    private final ModelCredentialRepository credentialRepository;
    private final ModelProviderRepository providerRepository;

    public ModelCredentialService(ModelCredentialRepository credentialRepository, ModelProviderRepository providerRepository) {
        this.credentialRepository = credentialRepository;
        this.providerRepository = providerRepository;
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
        credential.setApiKey(apiKey); // TODO: Encrypt this before saving
        credential.setStatus(ModelCredential.CredentialStatus.VALID);
        
        return credentialRepository.save(credential);
    }

    public boolean validateCredential(Long credentialId) {
        // In a real scenario, we would make a test call to the provider
        return credentialRepository.existsById(credentialId);
    }
    
    @Transactional
    public void deleteCredential(Long id) {
        credentialRepository.deleteById(id);
    }

    @Transactional
    public ModelCredential updateCredential(Long id, String apiKey) {
        ModelCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        credential.setApiKey(apiKey);
        return credentialRepository.save(credential);
    }

    @Transactional
    public ModelCredential toggleCredentialStatus(Long id, boolean enabled) {
        ModelCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        credential.setEnabled(enabled);
        return credentialRepository.save(credential);
    }
}
