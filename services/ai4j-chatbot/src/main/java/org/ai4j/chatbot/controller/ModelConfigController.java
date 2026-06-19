package org.ai4j.chatbot.controller;

import org.ai4j.chatbot.dto.ModelConfigResponse;
import org.ai4j.chatbot.entity.ModelConfig;
import org.ai4j.chatbot.service.ModelConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/configs")
public class ModelConfigController {

    private final ModelConfigService configService;

    public ModelConfigController(ModelConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public List<ModelConfigResponse> getAllConfigs() {
        return configService.getAllConfigs().stream()
                .map(ModelConfigResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ModelConfigResponse getConfigById(@PathVariable Long id) {
        return ModelConfigResponse.from(configService.getConfigById(id));
    }

    @PostMapping
    public ModelConfigResponse createConfig(@RequestBody ConfigRequest request) {
        return ModelConfigResponse.from(configService.createConfig(request.getName(), request.getProviderId(), request.getModelName(), request.getParameters()));
    }

    public static class ConfigRequest {
        private String name;
        private Long providerId;
        private String modelName;
        private Map<String, Object> parameters;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getProviderId() {
            return providerId;
        }

        public void setProviderId(Long providerId) {
            this.providerId = providerId;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }
}
