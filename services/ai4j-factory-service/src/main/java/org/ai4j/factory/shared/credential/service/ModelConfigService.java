package org.ai4j.factory.shared.credential.service;

import org.ai4j.factory.shared.credential.entity.ModelConfig;
import org.ai4j.factory.shared.credential.entity.ModelProvider;
import org.ai4j.factory.shared.credential.repository.ModelConfigRepository;
import org.ai4j.factory.shared.credential.repository.ModelProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ModelConfigService {

    private final ModelConfigRepository configRepository;
    private final ModelProviderRepository providerRepository;

    public ModelConfigService(ModelConfigRepository configRepository, ModelProviderRepository providerRepository) {
        this.configRepository = configRepository;
        this.providerRepository = providerRepository;
    }

    public List<ModelConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    public ModelConfig getConfigById(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Config not found"));
    }

    @Transactional
    public ModelConfig createConfig(String name, Long providerId, String modelName, Map<String, Object> params) {
        ModelProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        ModelConfig config = new ModelConfig();
        config.setName(name);
        config.setProvider(provider);
        config.setModelName(modelName);
        config.setParameters(params);
        config.setVersion("v1");

        return configRepository.save(config);
    }
}
