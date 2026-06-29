# Model Selector in ChatInput

**Date:** 2026-06-29
**Status:** Approved

## Summary

Move the model selector from ChatArea header into the ChatInput bottom toolbar, so both Chat and BI modes can independently select their model within the input area.

## Current State

- `ChatArea` manages credentials state and renders a model dropdown in the header (top-left)
- `BiArea` has no model selection at all
- `ChatInput` is a pure input component with no model awareness

## Target State

### ChatInput changes

New props:

```ts
interface ChatInputProps {
  onSend: (message: string) => void;
  isLoading: boolean;
  credentials: ModelCredential[];
  selectedCredential: ModelCredential | null;
  onCredentialChange: (cred: ModelCredential) => void;
}
```

Bottom toolbar layout (left group):

```
[Paperclip] [ImageIcon] [ModelName ▼]  ···  [Send/Mic]
```

The model selector is a small button showing the current model name with a ChevronDown icon. Clicking opens a dropdown list. Reuses the existing dropdown pattern from ChatArea (overlay + positioned list).

### ChatArea changes

- Remove the model selector from the header (lines 118-160 currently)
- Header only shows sidebar toggle button (when sidebar is closed)
- Pass `credentials`, `selectedCredential`, `onCredentialChange` to `ChatInput`
- `handleSendMessage` behavior unchanged — still reads `selectedCredential.id`

### BiArea changes

- Add credentials state management (load, select) — same pattern as ChatArea
- Pass credentials props to `ChatInput`
- `handleQuery` sends `selectedCredential.id` as a query param for the BI API endpoint
- Header stays as-is ("BI Analysis" title)

### Data flow

```
ChatArea / BiArea
  ├── loads credentials via credentialService
  ├── manages selectedCredential state
  └── passes everything to ChatInput
       └── ChatInput renders model selector in bottom toolbar
       └── ChatInput calls onSend(content) — parent uses selectedCredential.id
```

### Non-goals

- Chat history (the "Recent" list in sidebar) is not changed
- BI backend API changes are out of scope (just pass credential ID as query param)
- Model selector styling: small chip/button, rounded, matches existing toolbar button style
