package org.ai4j.factory.shared.credential.dto;

import java.util.Map;

public class OpenApiConfig {
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Map<String, Object> parameters;

    public OpenApiConfig() {}

    public OpenApiConfig(String provider, String baseUrl, String apiKey, String modelName, Map<String, Object> parameters) {
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.parameters = parameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Map<String, Object> parameters;

        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }
        public OpenApiConfig build() { return new OpenApiConfig(provider, baseUrl, apiKey, modelName, parameters); }
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
