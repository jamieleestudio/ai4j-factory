## 1. Frontend model option data

- [x] 1.1 Add frontend types for model configs and selectable model options containing `credentialId`, `modelName`, and provider metadata.
- [x] 1.2 Add a settings service method to fetch `/api/settings/configs`.
- [x] 1.3 Build selectable model options by pairing active credentials with model configs that share the same provider id.
- [x] 1.4 Select the first available concrete model option by default and handle the empty option state.

## 2. Selector UI

- [x] 2.1 Update `ChatInput` props to accept selectable model options and selected model option instead of raw credentials.
- [x] 2.2 Render dropdown options with `modelName` as primary text and provider name as secondary muted text.
- [x] 2.3 Render only the selected `modelName` on the collapsed selector button.
- [x] 2.4 Show an empty-state message when no concrete model option is available.

## 3. Request propagation

- [x] 3.1 Update BI query submission to require a selected model option and send its `credentialId` and `modelName`.
- [x] 3.2 Update chat streaming submission to require a selected model option and include its `credentialId` path value and `model` query parameter.
- [x] 3.3 Update backend chat streaming controller to pass the request `model` parameter into `ChatService.streamChat` instead of the hardcoded model.

## 4. Verification

- [x] 4.1 Add or update frontend tests for model option display and BI/chat request model propagation where test infrastructure exists.
- [x] 4.2 Add or update backend tests verifying the chat controller uses the requested model parameter where test infrastructure exists.
- [x] 4.3 Run frontend type checking/tests and backend tests relevant to model selection and chat/BI request handling.
- [x] 4.4 Manually verify the UI selector shows model name primary, provider secondary, and selected button displays only model name.
