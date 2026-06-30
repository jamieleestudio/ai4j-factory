# ChatClient Caffeine Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache `ChatClient` instances in memory by credential and model so repeated chat/BI calls reuse the same Spring AI/OpenAI client objects.

**Architecture:** `ChatClientFactory` owns a Caffeine cache keyed by `(credentialId, normalizedModelName)`. Cache misses read and validate the credential, build `ClientOptions`, sync/async OpenAI clients, `OpenAiChatModel`, and `ChatClient`; credential updates/deletes/toggles explicitly invalidate all cached clients for that credential.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring AI 2.0.0-M8, OpenAI Java SDK 4.36.0, Caffeine, JUnit 5, Mockito, AssertJ.

---

## File Structure

- Modify `services/ai4j-factory-service/pom.xml`
  - Add `com.github.ben-manes.caffeine:caffeine` as a production dependency.
- Modify `services/ai4j-factory-service/src/main/java/org/ai4j/factory/chat/ChatClientFactory.java`
  - Add the Caffeine cache.
  - Add a small cache key record.
  - Normalize empty model names to `deepseek-chat`.
  - Add `evict(Long credentialId, String modelName)` and `evictCredential(Long credentialId)`.
- Modify `services/ai4j-factory-service/src/main/java/org/ai4j/factory/shared/credential/service/ModelCredentialService.java`
  - Inject `ChatClientFactory`.
  - Invalidate cache entries after credential delete, update, and enabled/disabled toggle.
- Modify `services/ai4j-factory-service/src/test/java/org/ai4j/factory/chat/ChatClientFactoryTest.java`
  - Keep the existing Spring AI M8 async-client regression test.
  - Add cache-hit, cache-key, and explicit eviction tests.
- Create `services/ai4j-factory-service/src/test/java/org/ai4j/factory/shared/credential/service/ModelCredentialServiceTest.java`
  - Verify credential mutation methods call `ChatClientFactory.evictCredential(id)`.

---

### Task 1: Add Caffeine dependency

**Files:**
- Modify: `services/ai4j-factory-service/pom.xml:17-56`

- [ ] **Step 1: Add the dependency**

Insert this dependency after `spring-ai-starter-model-openai`:

```xml
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
    </dependency>
```

The dependency section should include:

```xml
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
    </dependency>
```

- [ ] **Step 2: Compile to verify dependency resolution**

Run from `services/ai4j-factory-service`:

```bash
JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot' mvn -q -DskipTests compile
```

Expected: exit code 0. JDK 25 warning output from Maven/Jansi is acceptable; Maven compilation errors are not.

---

### Task 2: Add failing cache-hit test

**Files:**
- Modify: `services/ai4j-factory-service/src/test/java/org/ai4j/factory/chat/ChatClientFactoryTest.java:1-37`

- [ ] **Step 1: Replace the test file with this version**

```java
package org.ai4j.factory.chat;

import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.entity.ModelProvider;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatClientFactoryTest {

    @Test
    void createDoesNotBuildDefaultClientWithoutCredentials() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        assertThatNoException().isThrownBy(() -> factory.create(1L, "gpt-4o-mini"));
    }

    @Test
    void createReusesCachedClientForSameCredentialAndModel() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient first = factory.create(1L, "gpt-4o-mini");
        ChatClient second = factory.create(1L, "gpt-4o-mini");

        assertThat(second).isSameAs(first);
        verify(repository, times(1)).findById(1L);
    }

    private static ModelCredential credential(Long id, String apiKey) {
        ModelProvider provider = new ModelProvider();
        provider.setId(1L);
        provider.setBaseUrl("https://api.openai.com/v1");

        ModelCredential credential = new ModelCredential();
        credential.setId(id);
        credential.setProvider(provider);
        credential.setApiKey(apiKey);
        credential.setEnabled(true);
        credential.setStatus(ModelCredential.CredentialStatus.VALID);
        return credential;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `services/ai4j-factory-service`:

```bash
JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot' mvn -q -Dtest=ChatClientFactoryTest test
```

Expected: `createReusesCachedClientForSameCredentialAndModel` fails because `second` is not the same instance as `first`, or because `findById(1L)` was called twice.

---

### Task 3: Implement Caffeine cache in ChatClientFactory

**Files:**
- Modify: `services/ai4j-factory-service/src/main/java/org/ai4j/factory/chat/ChatClientFactory.java:1-67`

- [ ] **Step 1: Replace `ChatClientFactory.java` with this implementation**

```java
package org.ai4j.factory.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.client.okhttp.OkHttpClient;
import com.openai.core.ClientOptions;
import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ChatClientFactory {

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final ModelCredentialRepository credentialRepository;
    private final Cache<ChatClientCacheKey, ChatClient> cache;

    public ChatClientFactory(ModelCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
    }

    public ChatClient create(Long credentialId, String modelName) {
        String normalizedModelName = normalizeModelName(modelName);
        return cache.get(new ChatClientCacheKey(credentialId, normalizedModelName),
                key -> buildChatClient(key.credentialId(), key.modelName()));
    }

    public void evict(Long credentialId, String modelName) {
        cache.invalidate(new ChatClientCacheKey(credentialId, normalizeModelName(modelName)));
    }

    public void evictCredential(Long credentialId) {
        cache.asMap().keySet().removeIf(key -> key.credentialId().equals(credentialId));
    }

    private ChatClient buildChatClient(Long credentialId, String modelName) {
        ModelCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new RuntimeException("Credential not found with id: " + credentialId));

        if (credential.getStatus() != ModelCredential.CredentialStatus.VALID || !credential.isEnabled()) {
            throw new RuntimeException("Credential is not valid or disabled");
        }

        String baseUrl = credential.getProvider().getBaseUrl();
        String apiKey = credential.getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("API key is empty for credential: " + credentialId);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new RuntimeException("Base URL is empty for provider: " + credential.getProvider().getId());
        }

        ClientOptions clientOptions = ClientOptions.builder()
                .httpClient(OkHttpClient.builder().build())
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(2)
                .build();

        OpenAIClient openAiClient = new OpenAIClientImpl(clientOptions);
        OpenAIClientAsync openAiClientAsync = new OpenAIClientAsyncImpl(clientOptions);

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .openAiClientAsync(openAiClientAsync)
                .options(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.7)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }

    private String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return DEFAULT_MODEL;
        }
        return modelName;
    }

    private record ChatClientCacheKey(Long credentialId, String modelName) {
    }
}
```

- [ ] **Step 2: Run cache test to verify it passes**

Run from `services/ai4j-factory-service`:

```bash
JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot' mvn -q -Dtest=ChatClientFactoryTest test
```

Expected: exit code 0.

---

### Task 4: Add model-key and eviction tests

**Files:**
- Modify: `services/ai4j-factory-service/src/test/java/org/ai4j/factory/chat/ChatClientFactoryTest.java`

- [ ] **Step 1: Add these test methods before the helper method**

```java
    @Test
    void createUsesDifferentCacheEntriesForDifferentModels() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient first = factory.create(1L, "gpt-4o-mini");
        ChatClient second = factory.create(1L, "gpt-4.1-mini");

        assertThat(second).isNotSameAs(first);
        verify(repository, times(2)).findById(1L);
    }

    @Test
    void createNormalizesBlankModelNameToDefaultCacheKey() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient first = factory.create(1L, null);
        ChatClient second = factory.create(1L, "");

        assertThat(second).isSameAs(first);
        verify(repository, times(1)).findById(1L);
    }

    @Test
    void evictCredentialRemovesAllModelsForCredential() {
        ModelCredentialRepository repository = mock(ModelCredentialRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(credential(1L, "test-api-key")));

        ChatClientFactory factory = new ChatClientFactory(repository);

        ChatClient first = factory.create(1L, "gpt-4o-mini");
        factory.create(1L, "gpt-4.1-mini");
        factory.evictCredential(1L);
        ChatClient afterEvict = factory.create(1L, "gpt-4o-mini");

        assertThat(afterEvict).isNotSameAs(first);
        verify(repository, times(3)).findById(1L);
    }
```

- [ ] **Step 2: Run tests to verify they pass**

Run from `services/ai4j-factory-service`:

```bash
JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot' mvn -q -Dtest=ChatClientFactoryTest test
```

Expected: exit code 0.

---

### Task 5: Add failing credential-service invalidation tests

**Files:**
- Create: `services/ai4j-factory-service/src/test/java/org/ai4j/factory/shared/credential/service/ModelCredentialServiceTest.java`

- [ ] **Step 1: Create `ModelCredentialServiceTest.java`**

```java
package org.ai4j.factory.shared.credential.service;

import org.ai4j.factory.chat.ChatClientFactory;
import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.ai4j.factory.shared.credential.repository.ModelProviderRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelCredentialServiceTest {

    @Test
    void updateCredentialEvictsCachedChatClients() {
        ModelCredentialRepository credentialRepository = mock(ModelCredentialRepository.class);
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        ModelCredential credential = credential(1L);
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(credential)).thenReturn(credential);

        ModelCredentialService service = new ModelCredentialService(
                credentialRepository, providerRepository, chatClientFactory);

        service.updateCredential(1L, "new-key");

        verify(chatClientFactory).evictCredential(1L);
    }

    @Test
    void deleteCredentialEvictsCachedChatClients() {
        ModelCredentialRepository credentialRepository = mock(ModelCredentialRepository.class);
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        ModelCredentialService service = new ModelCredentialService(
                credentialRepository, providerRepository, chatClientFactory);

        service.deleteCredential(1L);

        verify(chatClientFactory).evictCredential(1L);
    }

    @Test
    void toggleCredentialStatusEvictsCachedChatClients() {
        ModelCredentialRepository credentialRepository = mock(ModelCredentialRepository.class);
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ChatClientFactory chatClientFactory = mock(ChatClientFactory.class);
        ModelCredential credential = credential(1L);
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(credential)).thenReturn(credential);

        ModelCredentialService service = new ModelCredentialService(
                credentialRepository, providerRepository, chatClientFactory);

        service.toggleCredentialStatus(1L, false);

        verify(chatClientFactory).evictCredential(1L);
    }

    private static ModelCredential credential(Long id) {
        ModelCredential credential = new ModelCredential();
        credential.setId(id);
        credential.setApiKey("old-key");
        credential.setEnabled(true);
        credential.setStatus(ModelCredential.CredentialStatus.VALID);
        return credential;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `services/ai4j-factory-service`:

```bash
JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot' mvn -q -Dtest=ModelCredentialServiceTest test
```

Expected: compilation fails because `ModelCredentialService` does not yet have a constructor accepting `ChatClientFactory`, or tests fail because `evictCredential(1L)` is not called.

---

### Task 6: Invalidate cache from credential mutations

**Files:**
- Modify: `services/ai4j-factory-service/src/main/java/org/ai4j/factory/shared/credential/service/ModelCredentialService.java:1-65`

- [ ] **Step 1: Replace `ModelCredentialService.java` with this implementation**

```java
package org.ai4j.factory.shared.credential.service;

import org.ai4j.factory.chat.ChatClientFactory;
import org.ai4j.factory.shared.credential.entity.ModelCredential;
import org.ai4j.factory.shared.credential.entity.ModelProvider;
import org.ai4j.factory.shared.credential.repository.ModelCredentialRepository;
import org.ai4j.factory.shared.credential.repository.ModelProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModelCredentialService {

    private final ModelCredentialRepository credentialRepository;
    private final ModelProviderRepository providerRepository;
    private final ChatClientFactory chatClientFactory;

    public ModelCredentialService(ModelCredentialRepository credentialRepository,
                                  ModelProviderRepository providerRepository,
                                  ChatClientFactory chatClientFactory) {
        this.credentialRepository = credentialRepository;
        this.providerRepository = providerRepository;
        this.chatClientFactory = chatClientFactory;
    }

    public List<ModelCredential> getCredentialsByUserId(String userId) {
        return credentialRepository.findByUserId(userId);
    }

    @Transactional
    public ModelCredential addCredential(String userId, Long providerId, String apiKey) {
        ModelProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        ModelCredential credential = new ModelCredential();
        credential.setUserId(userId);
        credential.setProvider(provider);
        credential.setApiKey(apiKey);
        credential.setStatus(ModelCredential.CredentialStatus.VALID);

        return credentialRepository.save(credential);
    }

    public boolean validateCredential(Long credentialId) {
        return credentialRepository.existsById(credentialId);
    }

    @Transactional
    public void deleteCredential(Long id) {
        credentialRepository.deleteById(id);
        chatClientFactory.evictCredential(id);
    }

    @Transactional
    public ModelCredential updateCredential(Long id, String apiKey) {
        ModelCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        credential.setApiKey(apiKey);
        ModelCredential saved = credentialRepository.save(credential);
        chatClientFactory.evictCredential(id);
        return saved;
    }

    @Transactional
    public ModelCredential toggleCredentialStatus(Long id, boolean enabled) {
        ModelCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Credential not found"));
        credential.setEnabled(enabled);
        ModelCredential saved = credentialRepository.save(credential);
        chatClientFactory.evictCredential(id);
        return saved;
    }
}
```

- [ ] **Step 2: Run credential service tests**

Run from `services/ai4j-factory-service`:

```bash
JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot' mvn -q -Dtest=ModelCredentialServiceTest test
```

Expected: exit code 0.

---

### Task 7: Run focused regression suite

**Files:**
- Verify only; no file changes.

- [ ] **Step 1: Run both focused test classes**

Run from `services/ai4j-factory-service`:

```bash
JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot' mvn -q -Dtest=ChatClientFactoryTest,ModelCredentialServiceTest test
```

Expected: exit code 0. JDK 25 and Mockito dynamic-agent warnings are acceptable.

- [ ] **Step 2: Run full module tests**

Run from `services/ai4j-factory-service`:

```bash
JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot' mvn test
```

Expected: exit code 0. If unrelated integration tests fail because MySQL or external services are unavailable, record the exact failure and keep the focused regression suite as the evidence for this change.

---

## Self-Review

- Spec coverage: The plan covers Caffeine in-memory caching, `(credentialId, modelName)` cache keys, default model normalization, maximum size, idle expiration, explicit invalidation from credential mutations, and regression tests for Spring AI M8 sync/async client construction.
- Placeholder scan: No `TBD`, `TODO`, unspecified edge handling, or “similar to” shortcuts remain.
- Type consistency: `ChatClientFactory.evictCredential(Long)` is defined in Task 3 and used in Task 6; `ModelCredentialService` constructor is updated in Task 6 and matched by tests in Task 5.
