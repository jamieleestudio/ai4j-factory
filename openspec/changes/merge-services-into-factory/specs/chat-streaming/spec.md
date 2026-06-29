## ADDED Requirements

### Requirement: SSE streaming chat

The system SHALL support streaming chat via Server-Sent Events (SSE). The client sends a message with a credential ID and optional model name, and the server streams the LLM response token-by-token.

#### Scenario: Stream chat with valid credential

- **WHEN** a client connects to `/api/chat/stream/{credentialId}` with `message: "你好"` and a valid credential ID
- **THEN** the server returns an SSE stream with text chunks from the LLM
- **THEN** the stream completes with no error

#### Scenario: Stream chat with invalid credential

- **WHEN** a client connects with a disabled or non-existent credential ID
- **THEN** the server returns an error via SSE and closes the stream

#### Scenario: Stream chat with custom model

- **WHEN** a client specifies `model: "deepseek-v3"` in the request
- **THEN** the system uses the specified model instead of the default

### Requirement: Chat service resolves credentials

The ChatService SHALL resolve the LLM credential from the shared credential store before creating the chat model client.

#### Scenario: Resolve credential and create client

- **WHEN** ChatService receives a valid `credentialId`
- **THEN** it fetches the credential from `ModelCredentialRepository`
- **THEN** it configures an OpenAI-compatible chat client with the credential's base URL and API key
- **THEN** it streams the LLM response
