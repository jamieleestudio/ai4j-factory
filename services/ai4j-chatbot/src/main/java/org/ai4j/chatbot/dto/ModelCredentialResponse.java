package org.ai4j.chatbot.dto;

import org.ai4j.chatbot.entity.ModelCredential;
import java.time.LocalDateTime;

public class ModelCredentialResponse {
    private Long id;
    private String userId;
    private ModelProviderResponse provider;
    private String apiKey;
    private boolean enabled;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ModelCredentialResponse from(ModelCredential credential) {
        if (credential == null) {
            return null;
        }
        ModelCredentialResponse response = new ModelCredentialResponse();
        response.setId(credential.getId());
        response.setUserId(credential.getUserId());
        response.setProvider(ModelProviderResponse.from(credential.getProvider()));
        response.setApiKey(credential.getApiKey());
        response.setEnabled(credential.isEnabled());
        response.setStatus(credential.getStatus().name());
        response.setCreatedAt(credential.getCreatedAt());
        response.setUpdatedAt(credential.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ModelProviderResponse getProvider() {
        return provider;
    }

    public void setProvider(ModelProviderResponse provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
