## 1. New Module Setup

- [x] 1.1 Create `services/ai4j-factory-service` Maven module with `pom.xml` (Spring Boot Web, Spring AI Open AI Starter, MySQL Connector, Flyway, JPA)
- [x] 1.2 Create `FactoryApplication.java` main class with `@SpringBootApplication` at `org.ai4j.factory`
- [x] 1.3 Create `application.yml` with datasource `ai4j_factory`, server port 8080, and Spring AI settings

## 2. Shared Credential Migration

- [x] 2.1 Copy entity classes to `shared/credential/entity/` (ModelProvider, ModelCredential, ModelConfig, UserConfig) from ai4j-chatbot
- [x] 2.2 Copy repository interfaces to `shared/credential/repository/` from ai4j-chatbot
- [x] 2.3 Copy DTO classes to `shared/credential/dto/` (ModelProviderResponse, ModelCredentialResponse, ModelConfigResponse, UserConfigResponse) from ai4j-chatbot
- [x] 2.4 Copy service classes to `shared/credential/service/` (ModelProviderService, ModelCredentialService, ModelConfigService, UserConfigService) from ai4j-chatbot
- [x] 2.5 Copy and rename controller to `shared/credential/controller/SettingsController.java` with path `/api/settings/` (from `/api/model-providers` etc.)
- [x] 2.6 Update package declarations and imports in all migrated shared files to `org.ai4j.factory.shared.credential`

## 3. Chat Module Migration

- [x] 3.1 Copy `ChatService.java` to `chat/` package, update package to `org.ai4j.factory.chat`
- [x] 3.2 Copy `ChatStreamController.java` to `chat/ChatController.java`, update path to `/api/chat/stream`, update package to `org.ai4j.factory.chat`
- [x] 3.3 Update imports in chat module to reference `shared/credential/` packages

## 4. BI Module Migration

- [x] 4.1 Copy semantic layer classes to `bi/semantic/` (SemanticLayer, Subject, Metric, Dimension) from ai4j-chatbi
- [x] 4.2 Copy intent extraction classes to `bi/intent/` (QueryIntent, Filter, IntentExtractionService) from ai4j-chatbi
- [x] 4.3 Copy query classes to `bi/query/` (SqlBuilder, QueryExecutionService) from ai4j-chatbi
- [x] 4.4 Copy insight classes to `bi/insight/` (InsightResponse, InsightGenerationService) from ai4j-chatbi
- [x] 4.5 Copy `ChatBiController.java` to `bi/BiController.java`, update path to `/api/bi/query`, update package to `org.ai4j.factory.bi`
- [x] 4.6 Copy `orders.json` semantic layer file to `resources/semantic/`
- [x] 4.7 Update all package declarations and imports in bi module to `org.ai4j.factory.bi`
- [x] 4.8 Update BI module to resolve LLM credentials from shared credential store

## 5. Database Migration

- [x] 5.1 Copy Flyway migration `V1__init_schema.sql` from ai4j-chatbot to new module
- [x] 5.2 Verify migration script references correct tables (model_provider, model_credential, model_config, user_config)

## 6. Root Project Update

- [x] 6.1 Add `<module>services/ai4j-factory-service</module>` to root `pom.xml`
- [x] 6.2 Remove `<module>services/ai4j-chatbot</module>` from root `pom.xml`
- [x] 6.3 Remove `<module>services/ai4j-chatbi</module>` from root `pom.xml`

## 7. Frontend Updates

- [x] 7.1 Update `metadata.title` in `layout.tsx` to "AI4J Factory"
- [x] 7.2 Add "BI" menu item in Sidebar component (between "New Chat" and "Settings")
- [x] 7.3 Create BI page/component that calls `/api/bi/query` and renders insight response (summary + data table + chart placeholder)
- [x] 7.4 Update ChatArea to switch between chat mode and BI mode based on active menu
- [x] 7.5 Update CredentialManager API calls from `/api/model-providers` to `/api/settings/providers` (and related paths)
- [x] 7.6 Update ChatArea API calls to use `/api/chat/stream` path

## 8. Cleanup

- [x] 8.1 Delete `services/ai4j-chatbot` directory
- [x] 8.2 Delete `services/ai4j-chatbi` directory
- [x] 8.3 Run `mvn clean compile` to verify the new module compiles

## 9. Verification

- [x] 9.1 Start the application and verify Flyway migration runs successfully
- [x] 9.2 Test `/api/chat/stream` endpoint with a valid credential
- [x] 9.3 Test `/api/bi/query` endpoint with a sample question
- [x] 9.4 Test `/api/settings/providers` returns providers list
- [x] 9.5 Verify frontend: New Chat menu works with streaming
- [x] 9.6 Verify frontend: BI menu works with query and insight display
