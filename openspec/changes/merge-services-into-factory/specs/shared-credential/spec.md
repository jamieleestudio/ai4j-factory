## ADDED Requirements

### Requirement: Model provider management

The system SHALL support CRUD operations for LLM model providers. Each provider has a name, base URL, and optional description.

#### Scenario: Create a provider

- **WHEN** a user creates a provider with `name: "DeepSeek"`, `baseUrl: "https://api.deepseek.com"`
- **THEN** the provider is persisted and available for credential binding

#### Scenario: List all providers

- **WHEN** the user requests the provider list
- **THEN** the system returns all registered providers with their names and base URLs

#### Scenario: Delete an unused provider

- **WHEN** a provider has no associated credentials
- **THEN** the provider can be deleted

### Requirement: Model credential management

The system SHALL support CRUD operations for API credentials bound to a provider. Each credential has an API key, optional model name, status (valid/invalid), and enabled flag.

#### Scenario: Create a credential

- **WHEN** a user creates a credential with `apiKey: "sk-xxx"` bound to provider "DeepSeek"
- **THEN** the credential is stored and can be used for chat or BI queries

#### Scenario: Disable a credential

- **WHEN** a user sets a credential's `enabled` flag to false
- **THEN** the credential cannot be selected for new chat or BI sessions

#### Scenario: Credential status tracking

- **WHEN** a credential is created
- **THEN** its initial status is set to `VALID`

### Requirement: User configuration binding

The system SHALL allow a user to bind a default model configuration (credential + model name) to their user identity.

#### Scenario: Bind default config

- **WHEN** a user binds credential "DeepSeek" with model "deepseek-chat" as their default
- **THEN** subsequent chat sessions default to this credential and model

### Requirement: Shared credential access for chat and BI

Both the chat module and the BI module SHALL resolve LLM credentials from the same shared credential store.

#### Scenario: Chat uses shared credential

- **WHEN** a chat stream request specifies a `credentialId`
- **THEN** the chat module resolves the credential from `shared/credential/` to configure the LLM client

#### Scenario: BI uses shared credential

- **WHEN** a BI query request specifies a `credentialId`
- **THEN** the BI module resolves the credential from `shared/credential/` for intent extraction and insight generation

### Requirement: Settings API endpoints

The system SHALL expose credential management endpoints under `/api/settings/`.

#### Scenario: Provider CRUD endpoint

- **WHEN** a GET request is made to `/api/settings/providers`
- **THEN** the system returns all registered model providers

#### Scenario: Credential CRUD endpoint

- **WHEN** a POST request is made to `/api/settings/credentials`
- **THEN** the system creates a new credential and returns its details

#### Scenario: Config endpoint

- **WHEN** a GET request is made to `/api/settings/configs`
- **THEN** the system returns the current user's model configuration
