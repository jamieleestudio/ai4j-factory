package org.ai4j.chatbot.controller;

import org.ai4j.chatbot.dto.ModelCredentialResponse;
import org.ai4j.chatbot.entity.ModelCredential;
import org.ai4j.chatbot.service.ModelCredentialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/credentials")
public class ModelCredentialController {

    private final ModelCredentialService credentialService;

    public ModelCredentialController(ModelCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping
    public List<ModelCredentialResponse> getCredentials(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId) {
        return credentialService.getCredentialsByUserId(userId).stream()
                .map(ModelCredentialResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ModelCredentialResponse addCredential(@RequestHeader(value = "X-User-Id", defaultValue = "default-user") String userId,
                                         @RequestBody CredentialRequest request) {
        return ModelCredentialResponse.from(credentialService.addCredential(userId, request.getProviderId(), request.getApiKey()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCredential(@PathVariable Long id) {
        credentialService.deleteCredential(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ModelCredentialResponse updateCredential(@PathVariable Long id, @RequestBody CredentialRequest request) {
        return ModelCredentialResponse.from(credentialService.updateCredential(id, request.getApiKey()));
    }

    @PatchMapping("/{id}/status")
    public ModelCredentialResponse toggleCredentialStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return ModelCredentialResponse.from(credentialService.toggleCredentialStatus(id, enabled));
    }

    public static class CredentialRequest {
        private Long providerId;
        private String apiKey;

        public Long getProviderId() {
            return providerId;
        }

        public void setProviderId(Long providerId) {
            this.providerId = providerId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
