package org.ai4j.factory.shared.credential.dto;

import org.ai4j.factory.shared.credential.entity.ModelProvider;

import java.time.LocalDateTime;

public class ModelProviderResponse {
    private Long id;
    private String name;
    private String baseUrl;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ModelProviderResponse from(ModelProvider provider) {
        if (provider == null) return null;
        ModelProviderResponse response = new ModelProviderResponse();
        response.setId(provider.getId());
        response.setName(provider.getName());
        response.setBaseUrl(provider.getBaseUrl());
        response.setDescription(provider.getDescription());
        response.setCreatedAt(provider.getCreatedAt());
        response.setUpdatedAt(provider.getUpdatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
