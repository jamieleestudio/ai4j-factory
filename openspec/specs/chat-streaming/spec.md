## ADDED Requirements

### Requirement: Chat 逐 token 流式输出
系统 SHALL 在 Chat 模式下，将 LLM 生成的文本以 token 级别逐片推送到前端，每收到一个文本片段就立即发送一个 SSE 事件，而非等待完整响应后一次性发送。

#### Scenario: 用户发送聊天消息后逐字看到回复
- **WHEN** 用户在 Chat 页面发送一条消息
- **THEN** AI 回复文本以逐 token 方式渐进显示，每个新 token 追加到已显示文本末尾

#### Scenario: 流式响应完成
- **WHEN** LLM 生成完毕，Flux 流结束
- **THEN** SSE 连接关闭，前端停止加载状态

#### Scenario: 流式响应出错
- **WHEN** LLM 调用或网络传输过程中发生错误
- **THEN** SSE 连接关闭，前端显示错误状态

### Requirement: Chat 端点返回 Flux<String>
Chat 的流式端点 SHALL 直接返回 `Flux<String>` 类型，配合 `produces = text/event-stream`，由 Spring MVC 自动将每个 Flux 元素序列化为 SSE `data:` 事件。

#### Scenario: Controller 返回 Flux
- **WHEN** `GET /api/chat/stream/{credentialId}?message=...&model=...` 被调用
- **THEN** 响应 Content-Type 为 `text/event-stream`，每个 Flux 元素作为一个独立的 `data:` 行发送

### Requirement: OpenAiChatModel 显式开启 streaming
`ChatClientFactory` 在构建 `OpenAiChatModel` 时 SHALL 显式配置 streaming 相关参数，确保底层 OpenAI SDK 向 LLM API 发送 `stream: true` 请求。

#### Scenario: LLM API 收到 streaming 请求
- **WHEN** 调用 `chatClient.prompt().user(message).stream().chatResponse()`
- **THEN** 底层 HTTP 请求 body 中包含 `"stream": true`
