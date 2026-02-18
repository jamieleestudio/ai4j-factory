package org.ai4j.chatbot.dto;

import org.ai4j.chatbot.entity.UserConfig;
import java.time.LocalDateTime;

public class UserConfigResponse {
    private Long id;
    private String userId;
    private ModelCredentialResponse credential;
    private ModelConfigResponse config;
    private String alias;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserConfigResponse from(UserConfig userConfig) {
        if (userConfig == null) {
            return null;
        }
        UserConfigResponse response = new UserConfigResponse();
        response.setId(userConfig.getId());
        response.setUserId(userConfig.getUserId());
        response.setCredential(ModelCredentialResponse.from(userConfig.getCredential()));
        response.setConfig(ModelConfigResponse.from(userConfig.getConfig()));
        response.setAlias(userConfig.getAlias());
        response.setCreatedAt(userConfig.getCreatedAt());
        response.setUpdatedAt(userConfig.getUpdatedAt());
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

    public ModelCredentialResponse getCredential() {
        return credential;
    }

    public void setCredential(ModelCredentialResponse credential) {
        this.credential = credential;
    }

    public ModelConfigResponse getConfig() {
        return config;
    }

    public void setConfig(ModelConfigResponse config) {
        this.config = config;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
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
