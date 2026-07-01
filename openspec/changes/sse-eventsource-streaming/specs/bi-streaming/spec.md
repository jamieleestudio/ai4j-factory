## MODIFIED Requirements

### Requirement: BI 端点返回 SSE 流
BI 查询端点 SHALL 返回 `text/event-stream` 类型，使用 GET 方法接收 query string 参数，以 SSE 格式流式返回 JSON envelope 事件。

#### Scenario: GET SSE 响应
- **WHEN** `GET /api/bi/query?question=<urlencoded>&credentialId=<n>&modelName=<str>&sessionId=<uuid>` 被调用
- **THEN** 响应 Content-Type 为 `text/event-stream`，按序推送 `status`、`intent`、`chunk`、`result`、`done` 事件（每个 `data:` 行为 JSON 对象）

#### Scenario: 参数缺失降级
- **WHEN** `question` 或 `credentialId` 参数缺失
- **THEN** 返回 HTTP 400，响应体为错误说明

### Requirement: 前端 BI 使用 EventSource 读取 SSE
前端 BI 组件 SHALL 使用浏览器原生 `EventSource` API 读取 SSE 流，通过 `JSON.parse` 解析每个 `data:` 行。禁止使用 `fetch + getReader()` 手动读取，因为该方法会在浏览器/Node 内部把多个 SSE 帧攒批返回，破坏逐 token 流式效果。

#### Scenario: 订阅 SSE
- **WHEN** 前端发起 BI 查询
- **THEN** 构造 `EventSource` GET 请求，在 `onmessage` 回调里 `JSON.parse(e.data)` 后按 `type` 分发到对应回调

#### Scenario: 流式读取完成
- **WHEN** SSE 流收到 `done` 事件
- **THEN** 前端停止加载状态，最终结果渲染完成，主动调用 `es.close()` 防止自动重连

#### Scenario: 读取过程出错
- **WHEN** 收到 `error` 事件，或 `EventSource` 触发 `onerror`
- **THEN** 前端显示错误状态，已接收的部分内容保留显示，主动调用 `es.close()` 防止自动重连
