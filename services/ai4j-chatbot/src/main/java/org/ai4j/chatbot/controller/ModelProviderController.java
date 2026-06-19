package org.ai4j.chatbot.controller;

import org.ai4j.chatbot.dto.ModelProviderResponse;
import org.ai4j.chatbot.entity.ModelProvider;
import org.ai4j.chatbot.service.ModelProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/providers")
public class ModelProviderController {

    private final ModelProviderService providerService;

    public ModelProviderController(ModelProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public List<ModelProviderResponse> getAllProviders() {
        return providerService.getAllProviders().stream()
                .map(ModelProviderResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ModelProviderResponse getProviderById(@PathVariable Long id) {
        return ModelProviderResponse.from(providerService.getProviderById(id));
    }

    @PostMapping
    public ModelProviderResponse createProvider(@RequestBody ModelProvider provider) {
        return ModelProviderResponse.from(providerService.createProvider(provider));
    }

    @PutMapping("/{id}")
    public ModelProviderResponse updateProvider(@PathVariable Long id, @RequestBody ModelProvider provider) {
        return ModelProviderResponse.from(providerService.updateProvider(id, provider));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProvider(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return ResponseEntity.ok().build();
    }
}
