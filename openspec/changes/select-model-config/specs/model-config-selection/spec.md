## ADDED Requirements

### Requirement: Select concrete model configuration
The system SHALL allow users to select a concrete model configuration for chat and BI interactions, not only a provider or credential.

#### Scenario: Model options are built from active credentials and provider configs
- **WHEN** the frontend loads active credentials and available model configs
- **THEN** it SHALL create selectable model options by pairing each active credential with model configs for the same provider

#### Scenario: No concrete model is available
- **WHEN** no active credential has a matching model config
- **THEN** the selector SHALL show an empty-state message and chat or BI submission SHALL not send a request without a concrete model selection

### Requirement: Display model name as primary selector text
The system SHALL render model selection options with the model name as the primary text and provider name as secondary muted text.

#### Scenario: User opens model selector
- **WHEN** the user opens the model selector
- **THEN** each option SHALL display `modelName` as the primary line and provider name as the secondary line

#### Scenario: User selects a model
- **WHEN** the user selects a model option
- **THEN** the collapsed selector button SHALL display only the selected `modelName`

### Requirement: Send selected model through BI requests
The system SHALL send the selected credential id and model name when submitting BI questions.

#### Scenario: BI question is submitted with selected model
- **WHEN** the user submits a BI question after selecting a model option
- **THEN** the request body SHALL include `credentialId` from the selected option and `modelName` from the selected option

### Requirement: Send selected model through chat requests
The system SHALL send the selected model name when opening chat streaming requests.

#### Scenario: Chat message is submitted with selected model
- **WHEN** the user submits a chat message after selecting a model option
- **THEN** the EventSource URL SHALL include the selected credential id in the path and the selected `modelName` as the `model` query parameter

### Requirement: Backend chat uses requested model
The backend SHALL use the requested chat model parameter when creating the chat client for streaming responses.

#### Scenario: Chat stream request includes model parameter
- **WHEN** the chat streaming endpoint receives a `model` query parameter
- **THEN** it SHALL pass that value to the chat service instead of a hardcoded model name
