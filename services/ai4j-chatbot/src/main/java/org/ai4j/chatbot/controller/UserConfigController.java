package org.ai4j.chatbot.controller;

import org.ai4j.chatbot.dto.OpenApiConfig;
import org.ai4j.chatbot.dto.UserConfigResponse;
import org.ai4j.chatbot.entity.UserConfig;
import org.ai4j.chatbot.service.UserConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user-configs")
public class UserConfigController {

    private final UserConfigService userConfigService;

    public UserConfigController(UserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    @GetMapping
    public List<UserConfigResponse> getUserConfigs(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId) {
        return userConfigService.getUserConfigs(userId).stream()
                .map(UserConfigResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping
    public UserConfigResponse bindUserConfig(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId,
                                     @RequestBody BindConfigRequest request) {
        return UserConfigResponse.from(userConfigService.bindUserConfig(userId, request.getCredentialId(), request.getConfigId(), request.getAlias()));
    }

    @GetMapping("/generate")
    public OpenApiConfig generateConfig(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId,
                                        @RequestParam String alias) {
        return userConfigService.generateOpenApiConfig(userId, alias);
    }

    public static class BindConfigRequest {
        private Long credentialId;
        private Long configId;
        private String alias;

        public Long getCredentialId() {
            return credentialId;
        }

        public void setCredentialId(Long credentialId) {
            this.credentialId = credentialId;
        }

        public Long getConfigId() {
            return configId;
        }

        public void setConfigId(Long configId) {
            this.configId = configId;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }
}
