## ADDED Requirements

### Requirement: SSE 端点必须使用 GET 方法
所有 SSE 端点（BI 查询、Chat 流式）SHALL 使用 HTTP GET 方法，参数通过 query string 传递，禁止使用 POST + 请求体。原因是前端必须使用浏览器原生 `EventSource` 消费 SSE 流，而 `EventSource` 规范只支持 GET。

#### Scenario: BI 查询端点
- **WHEN** 前端发起 BI 查询
- **THEN** 请求为 `GET /api/bi/query?question=<urlencoded>&credentialId=<n>&modelName=<str>&sessionId=<uuid>`，响应 Content-Type 为 `text/event-stream`

#### Scenario: Chat 流式端点
- **WHEN** 前端发起 Chat 流式请求
- **THEN** 请求为 `GET /api/chat/stream/{credentialId}?message=<urlencoded>&model=<str>`，响应 Content-Type 为 `text/event-stream`

### Requirement: 前端必须使用浏览器原生 EventSource
前端 SSE 消费层 SHALL 使用浏览器原生 `EventSource` API，禁止使用 `fetch` + `getReader()` + 手动按行解析的方案。`EventSource` 内部按 SSE 帧边界触发 `onmessage` 回调，避免 `getReader()` 在底层按 TCP 包或内部 buffer 策略攒批多个事件。

#### Scenario: 订阅 SSE 流
- **WHEN** 前端订阅一个 SSE 端点
- **THEN** 构造 `new EventSource(url, { withCredentials: true })`，在每个 `onmessage` 事件中 `JSON.parse(e.data)` 后按 `type` 分发到对应回调

#### Scenario: 流结束主动关闭
- **WHEN** 前端收到 `done` 事件
- **THEN** 调用 `es.close()` 主动关闭连接，避免 `EventSource` 默认的自动重连行为

#### Scenario: 异常中断
- **WHEN** `EventSource` 触发 `onerror`（非正常关闭）
- **THEN** 前端通过 `onError` 回调通知调用方，调用 `onDone` 回调，并主动 `close()` 防止重连

### Requirement: 统一的 SSE 订阅工具
前端 SHALL 提供统一的 `subscribeSSE(url, callbacks)` 工具函数，封装 `EventSource` 构造、`onmessage` 解析、`onerror` 处理、`close` 生命周期。BI 和 Chat 组件 SHALL 共用此工具，禁止各自手搓 `EventSource` 调用。

#### Scenario: 工具签名
- **WHEN** 调用方订阅 SSE
- **THEN** `subscribeSSE(url, callbacks)` 返回 `{ close: () => void }` 订阅对象，调用方在组件卸载或流结束时调用 `close()`

#### Scenario: 回调接口
- **WHEN** `subscribeSSE` 收到一个 SSE 帧
- **THEN** 按 `type` 字段分发到 `onStatus` / `onIntent` / `onChunk` / `onResult` / `onClarification` / `onError` / `onDone` 回调之一
