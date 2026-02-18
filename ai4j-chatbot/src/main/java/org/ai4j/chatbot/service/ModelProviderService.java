package org.ai4j.chatbot.service;

import org.ai4j.chatbot.entity.ModelProvider;
import org.ai4j.chatbot.repository.ModelProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModelProviderService {

    private final ModelProviderRepository providerRepository;

    public ModelProviderService(ModelProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    public List<ModelProvider> getAllProviders() {
        return providerRepository.findAll();
    }

    public ModelProvider getProviderById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Provider not found with id: " + id));
    }

    @Transactional
    public ModelProvider createProvider(ModelProvider provider) {
        if (providerRepository.existsByName(provider.getName())) {
            throw new RuntimeException("Provider with name " + provider.getName() + " already exists");
        }
        return providerRepository.save(provider);
    }

    @Transactional
    public ModelProvider updateProvider(Long id, ModelProvider providerDetails) {
        ModelProvider provider = getProviderById(id);
        provider.setName(providerDetails.getName());
        provider.setBaseUrl(providerDetails.getBaseUrl());
        provider.setDescription(providerDetails.getDescription());
        return providerRepository.save(provider);
    }

    @Transactional
    public void deleteProvider(Long id) {
        if (!providerRepository.existsById(id)) {
            throw new RuntimeException("Provider not found with id: " + id);
        }
        providerRepository.deleteById(id);
    }
}
