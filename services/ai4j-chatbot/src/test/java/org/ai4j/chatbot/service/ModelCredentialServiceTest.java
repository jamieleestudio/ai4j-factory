package org.ai4j.chatbot.service;

import org.ai4j.chatbot.entity.ModelCredential;
import org.ai4j.chatbot.entity.ModelProvider;
import org.ai4j.chatbot.repository.ModelCredentialRepository;
import org.ai4j.chatbot.repository.ModelProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ModelCredentialServiceTest {

    @Mock
    private ModelCredentialRepository credentialRepository;

    @Mock
    private ModelProviderRepository providerRepository;

    @InjectMocks
    private ModelCredentialService credentialService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void addCredential_shouldSaveCredential() {
        Long providerId = 1L;
        String userId = "test-user";
        String apiKey = "test-key";
        
        ModelProvider provider = new ModelProvider();
        provider.setId(providerId);
        
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(credentialRepository.save(any(ModelCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelCredential result = credentialService.addCredential(userId, providerId, apiKey);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(apiKey, result.getApiKey());
        assertEquals(provider, result.getProvider());
        assertTrue(result.isEnabled());
        verify(credentialRepository, times(1)).save(any(ModelCredential.class));
    }

    @Test
    void updateCredential_shouldUpdateApiKey() {
        Long credentialId = 1L;
        String newApiKey = "new-key";
        
        ModelCredential existingCredential = new ModelCredential();
        existingCredential.setId(credentialId);
        existingCredential.setApiKey("old-key");

        when(credentialRepository.findById(credentialId)).thenReturn(Optional.of(existingCredential));
        when(credentialRepository.save(any(ModelCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelCredential result = credentialService.updateCredential(credentialId, newApiKey);

        assertEquals(newApiKey, result.getApiKey());
        verify(credentialRepository, times(1)).save(existingCredential);
    }

    @Test
    void toggleCredentialStatus_shouldUpdateEnabledStatus() {
        Long credentialId = 1L;
        
        ModelCredential existingCredential = new ModelCredential();
        existingCredential.setId(credentialId);
        existingCredential.setEnabled(true);

        when(credentialRepository.findById(credentialId)).thenReturn(Optional.of(existingCredential));
        when(credentialRepository.save(any(ModelCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelCredential result = credentialService.toggleCredentialStatus(credentialId, false);

        assertFalse(result.isEnabled());
        verify(credentialRepository, times(1)).save(existingCredential);
    }
}
