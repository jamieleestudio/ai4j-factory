package org.ai4j.factory.shared.credential.dto;

import org.ai4j.factory.shared.credential.entity.ModelConfig;

import java.time.LocalDateTime;
import java.util.Map;

public class ModelConfigResponse {
    private Long id;
    private String name;
    private ModelProviderResponse provider;
    private String modelName;
    private Map<String, Object> parameters;
    private String version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ModelConfigResponse from(ModelConfig config) {
        if (config == null) return null;
        ModelConfigResponse response = new ModelConfigResponse();
        response.setId(config.getId());
        response.setName(config.getName());
        response.setProvider(ModelProviderResponse.from(config.getProvider()));
        response.setModelName(config.getModelName());
        response.setParameters(config.getParameters());
        response.setVersion(config.getVersion());
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ModelProviderResponse getProvider() { return provider; }
    public void setProvider(ModelProviderResponse provider) { this.provider = provider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
