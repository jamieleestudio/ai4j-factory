package org.ai4j.chatbot.service;

import org.ai4j.chatbot.dto.OpenApiConfig;
import org.ai4j.chatbot.entity.ModelConfig;
import org.ai4j.chatbot.entity.ModelCredential;
import org.ai4j.chatbot.entity.UserConfig;
import org.ai4j.chatbot.repository.ModelConfigRepository;
import org.ai4j.chatbot.repository.ModelCredentialRepository;
import org.ai4j.chatbot.repository.UserConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserConfigService {

    private final UserConfigRepository userConfigRepository;
    private final ModelCredentialRepository credentialRepository;
    private final ModelConfigRepository configRepository;

    public UserConfigService(UserConfigRepository userConfigRepository, ModelCredentialRepository credentialRepository, ModelConfigRepository configRepository) {
        this.userConfigRepository = userConfigRepository;
        this.credentialRepository = credentialRepository;
        this.configRepository = configRepository;
    }

    @Transactional
    public UserConfig bindUserConfig(String userId, Long credentialId, Long configId, String alias) {
        if (userConfigRepository.findByUserIdAndAlias(userId, alias).isPresent()) {
            throw new RuntimeException("Config alias already exists for this user");
        }

        ModelCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        
        if (!credential.getUserId().equals(userId)) {
            throw new RuntimeException("Credential does not belong to user");
        }

        ModelConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        if (!credential.getProvider().getId().equals(config.getProvider().getId())) {
            throw new RuntimeException("Credential provider does not match config provider");
        }

        UserConfig userConfig = new UserConfig();
        userConfig.setUserId(userId);
        userConfig.setCredential(credential);
        userConfig.setConfig(config);
        userConfig.setAlias(alias);

        return userConfigRepository.save(userConfig);
    }

    public List<UserConfig> getUserConfigs(String userId) {
        return userConfigRepository.findByUserId(userId);
    }

    public OpenApiConfig generateOpenApiConfig(String userId, String alias) {
        UserConfig userConfig = userConfigRepository.findByUserIdAndAlias(userId, alias)
                .orElseThrow(() -> new RuntimeException("User config not found"));

        return OpenApiConfig.builder()
                .provider(userConfig.getCredential().getProvider().getName())
                .baseUrl(userConfig.getCredential().getProvider().getBaseUrl())
                .apiKey(userConfig.getCredential().getApiKey())
                .modelName(userConfig.getConfig().getModelName())
                .parameters(userConfig.getConfig().getParameters())
                .build();
    }
}
