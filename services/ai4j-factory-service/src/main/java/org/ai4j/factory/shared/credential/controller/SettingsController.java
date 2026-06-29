package org.ai4j.factory.shared.credential.controller;

import org.ai4j.factory.shared.credential.dto.ModelConfigResponse;
import org.ai4j.factory.shared.credential.dto.ModelCredentialResponse;
import org.ai4j.factory.shared.credential.dto.ModelProviderResponse;
import org.ai4j.factory.shared.credential.dto.OpenApiConfig;
import org.ai4j.factory.shared.credential.dto.UserConfigResponse;
import org.ai4j.factory.shared.credential.entity.ModelProvider;
import org.ai4j.factory.shared.credential.service.ModelConfigService;
import org.ai4j.factory.shared.credential.service.ModelCredentialService;
import org.ai4j.factory.shared.credential.service.ModelProviderService;
import org.ai4j.factory.shared.credential.service.UserConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final ModelProviderService providerService;
    private final ModelCredentialService credentialService;
    private final ModelConfigService configService;
    private final UserConfigService userConfigService;

    public SettingsController(ModelProviderService providerService,
                              ModelCredentialService credentialService,
                              ModelConfigService configService,
                              UserConfigService userConfigService) {
        this.providerService = providerService;
        this.credentialService = credentialService;
        this.configService = configService;
        this.userConfigService = userConfigService;
    }

    // --- Providers ---

    @GetMapping("/providers")
    public List<ModelProviderResponse> getAllProviders() {
        return providerService.getAllProviders().stream()
                .map(ModelProviderResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/providers/{id}")
    public ModelProviderResponse getProviderById(@PathVariable Long id) {
        return ModelProviderResponse.from(providerService.getProviderById(id));
    }

    @PostMapping("/providers")
    public ModelProviderResponse createProvider(@RequestBody ModelProvider provider) {
        return ModelProviderResponse.from(providerService.createProvider(provider));
    }

    @PutMapping("/providers/{id}")
    public ModelProviderResponse updateProvider(@PathVariable Long id, @RequestBody ModelProvider provider) {
        return ModelProviderResponse.from(providerService.updateProvider(id, provider));
    }

    @DeleteMapping("/providers/{id}")
    public ResponseEntity<?> deleteProvider(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return ResponseEntity.ok().build();
    }

    // --- Credentials ---

    @GetMapping("/credentials")
    public List<ModelCredentialResponse> getCredentials(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId) {
        return credentialService.getCredentialsByUserId(userId).stream()
                .map(ModelCredentialResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/credentials")
    public ModelCredentialResponse addCredential(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId,
                                                  @RequestBody CredentialRequest request) {
        return ModelCredentialResponse.from(credentialService.addCredential(userId, request.getProviderId(), request.getApiKey()));
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<?> deleteCredential(@PathVariable Long id) {
        credentialService.deleteCredential(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/credentials/{id}")
    public ModelCredentialResponse updateCredential(@PathVariable Long id, @RequestBody CredentialRequest request) {
        return ModelCredentialResponse.from(credentialService.updateCredential(id, request.getApiKey()));
    }

    @PatchMapping("/credentials/{id}/status")
    public ModelCredentialResponse toggleCredentialStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return ModelCredentialResponse.from(credentialService.toggleCredentialStatus(id, enabled));
    }

    // --- Configs ---

    @GetMapping("/configs")
    public List<ModelConfigResponse> getAllConfigs() {
        return configService.getAllConfigs().stream()
                .map(ModelConfigResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/configs/{id}")
    public ModelConfigResponse getConfigById(@PathVariable Long id) {
        return ModelConfigResponse.from(configService.getConfigById(id));
    }

    @PostMapping("/configs")
    public ModelConfigResponse createConfig(@RequestBody ConfigRequest request) {
        return ModelConfigResponse.from(configService.createConfig(request.getName(), request.getProviderId(), request.getModelName(), request.getParameters()));
    }

    // --- User Configs ---

    @GetMapping("/user-configs")
    public List<UserConfigResponse> getUserConfigs(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId) {
        return userConfigService.getUserConfigs(userId).stream()
                .map(UserConfigResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/user-configs")
    public UserConfigResponse bindUserConfig(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId,
                                              @RequestBody BindConfigRequest request) {
        return UserConfigResponse.from(userConfigService.bindUserConfig(userId, request.getCredentialId(), request.getConfigId(), request.getAlias()));
    }

    @GetMapping("/user-configs/generate")
    public OpenApiConfig generateConfig(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId,
                                        @RequestParam String alias) {
        return userConfigService.generateOpenApiConfig(userId, alias);
    }

    // --- Request DTOs ---

    public static class CredentialRequest {
        private Long providerId;
        private String apiKey;

        public Long getProviderId() { return providerId; }
        public void setProviderId(Long providerId) { this.providerId = providerId; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class ConfigRequest {
        private String name;
        private Long providerId;
        private String modelName;
        private Map<String, Object> parameters;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getProviderId() { return providerId; }
        public void setProviderId(Long providerId) { this.providerId = providerId; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }

    public static class BindConfigRequest {
        private Long credentialId;
        private Long configId;
        private String alias;

        public Long getCredentialId() { return credentialId; }
        public void setCredentialId(Long credentialId) { this.credentialId = credentialId; }
        public Long getConfigId() { return configId; }
        public void setConfigId(Long configId) { this.configId = configId; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
    }
}
