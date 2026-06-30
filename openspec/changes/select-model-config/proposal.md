## Why

The current frontend model selector effectively selects a provider credential, so users can pick DeepSeek but not a concrete model such as `deepseek-chat`. BI and chat requests need to pass the selected model name so the backend creates clients for the intended provider model instead of relying on defaults or hardcoded values.

## What Changes

- Replace the chat input model selector behavior with concrete model configuration selection.
- Show each model option with `modelName` as the primary text and provider name as secondary muted text.
- Show only the selected `modelName` on the collapsed selector button.
- Send both `credentialId` and `modelName` in BI query requests.
- Send the selected `modelName` as the `model` query parameter in chat streaming requests.
- Update backend chat streaming to use the requested model name instead of a hardcoded model.

## Capabilities

### New Capabilities
- `model-config-selection`: Select a concrete provider model configuration in the frontend and pass its model name through BI and chat request flows.

### Modified Capabilities

## Impact

- Frontend types and services for settings model configs/user configs.
- `ChatInput` model selector UI and selected value shape.
- Chat and BI container components that load selectable models and send requests.
- Backend chat streaming controller model parameter handling.
- Tests for request payload/query parameter construction and backend model propagation.
