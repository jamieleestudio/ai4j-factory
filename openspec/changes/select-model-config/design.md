## Context

The frontend currently passes around `ModelCredential` as the selector value, and the selector renders `credential.provider.name`. This lets users choose a credential/provider, but not a concrete model configuration. The backend already has model config concepts (`ModelConfigResponse.modelName`) and BI already accepts `modelName`, while chat streaming currently accepts a `model` request parameter but does not use it.

## Goals / Non-Goals

**Goals:**
- Let users select a concrete configured model instead of only a provider credential.
- Render model options with `modelName` as the primary label and provider name as muted secondary text.
- Keep the collapsed selector compact by showing only the selected `modelName`.
- Pass `credentialId` and `modelName` through both BI and chat flows.
- Remove the backend chat streaming hardcoded model selection.

**Non-Goals:**
- Creating or editing model configs in this change.
- Changing provider or credential persistence schemas.
- Implementing model discovery from external provider APIs.
- Changing BI intent/insight prompting behavior beyond using the selected model.

## Decisions

1. Use model configuration as the UI selector concept.
   - The selector options should be derived from configured models rather than directly from provider credentials.
   - Rationale: `modelName` belongs to model config, while `credentialId` belongs to credentials. Requests need both.
   - Alternative considered: Add `modelName` to credentials. Rejected because one credential can be valid for multiple models under the same provider.

2. Pair active credentials with compatible model configs by provider.
   - For each active credential, include configs whose provider id matches the credential provider id.
   - Rationale: The backend chat client requires a credential id for API access and a model name for model selection.
   - Alternative considered: Use user-config aliases only. Rejected for this change because the current chat/BI request shape needs direct `credentialId` and `modelName` and user-config binding may not exist for every available provider model.

3. Keep selector display focused on model identity.
   - Dropdown option: primary `modelName`, secondary provider name.
   - Collapsed button: selected `modelName` only.
   - Rationale: Users mainly choose models, while provider is useful as supporting context.
   - Alternative considered: Display provider and model on one line. Rejected because it gives provider too much visual weight for this workflow.

4. Preserve backend request contracts with minimal additions.
   - BI continues posting `credentialId` and `modelName`.
   - Chat continues using the existing `model` query parameter and backend passes it into `ChatService.streamChat`.
   - Rationale: This avoids introducing new API endpoints or changing service boundaries.

## Risks / Trade-offs

- Duplicate model names across providers can look identical in the collapsed selector → The dropdown still shows provider as secondary context; the selected button intentionally remains compact per requested UI behavior.
- A provider may have active credentials but no configured models → No selectable model should be generated for that provider until a config exists.
- Multiple credentials for the same provider can create repeated model options → Each option remains internally distinct by credential id; later work can add aliases or credential labels if needed.
- Existing fallback defaults may hide missing selection bugs → Chat and BI containers should require/select a concrete option before sending rather than silently using provider names.
