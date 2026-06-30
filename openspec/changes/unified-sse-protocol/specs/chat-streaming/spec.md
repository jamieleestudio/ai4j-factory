## MODIFIED Requirements

### Requirement: Chat 逐 token 流式输出
系统 SHALL 在 Chat 模式下，将 LLM 生成的文本以 token 级别逐片推送到前端，每个 token 作为一个 `chunk` 事件（JSON envelope）推送，而非等待完整响应后一次性发送。

#### Scenario: 用户发送聊天消息后逐字看到回复
- **WHEN** 用户在 Chat 页面发送一条消息
- **THEN** AI 回复文本以逐 token 方式渐进显示，前端收到 `{"type":"chunk","content":"<token>"}` 事件，`content` 追加到已显示文本末尾

#### Scenario: 流式响应完成
- **WHEN** LLM 生成完毕，Flux 流结束
- **THEN** 后端推送 `{"type":"done"}` 事件后关闭 SSE 连接，前端停止加载状态

#### Scenario: 流式响应出错
- **WHEN** LLM 调用或网络传输过程中发生错误
- **THEN** 后端推送 `{"type":"error","message":"..."}` 事件后关闭 SSE 连接，前端显示错误状态

### Requirement: Chat 端点返回 SSE 流
Chat 的流式端点 SHALL 返回 `text/event-stream` 类型，推送 JSON envelope 事件（`chunk` + `done`/`error`）。Chat 端点 SHALL 不推送 `status`、`intent`、`result` 事件。

#### Scenario: Controller 返回 SSE 流
- **WHEN** `GET /api/chat/stream/{credentialId}?message=...&model=...` 被调用
- **THEN** 响应 Content-Type 为 `text/event-stream`，每个 `data:` 行为 JSON 对象，按序推送 `chunk` 事件，结尾推送 `done` 或 `error`
