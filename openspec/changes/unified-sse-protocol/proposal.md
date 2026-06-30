## Why

Chat 和 BI 当前的 SSE 返回数据没有统一的数据模型：BI 用 `[progress]`/`[chunk]`/`[result]` 字符串前缀手搓事件类型，图表类型 `<<CHART:bar>>` 被塞进 LLM 自然语言正文里靠正则抠取；Chat 则是裸 `Flux<String>`，连事件类型都没有。两端契约只存在于字符串拼接代码里，没有 DTO，前端要用两套解析器，且真正的「思考过程」（意图语义层）完全丢弃，用户看不到 AI 如何理解问题。需要定义一套统一的事件 envelope，让前后端契约显式化，并把意图语义层作为 thinking 暴露给用户。

## What Changes

- **定义统一 SSE 事件 envelope**：所有 SSE 事件以 JSON 对象表示，含 `type` 鉴别字段。事件类型：`status`（进度文案）、`intent`（意图语义层）、`chunk`（文本 token）、`result`（结构化结果）、`error`、`done`
- **BI 端采用统一 envelope** **BREAKING**：`BiController` 从 `[tag] payload` 字符串前缀改为推送 JSON 事件；新增 `intent` 事件，在意图提取完成后推送 `{ subject, metrics, dimensions, filters }`；`<<CHART:>>` 标记改为后端内部约定，在 chunk 推送给前端前剥离，前端永远看不到
- **Chat 端采用统一 envelope** **BREAKING**：`ChatService`/`ChatController` 从裸 `Flux<String>` 改为推送 `chunk` + `done` 事件（Chat 不发 `status`/`intent`/`result`）
- **`result` 事件采用窄口径**：BI 的 `result` 仅含 `{ chartType, data, rowCount }`，不暴露 SQL 和意图（意图已由 `intent` 事件单独推送）
- **前端统一 SSE 解析**：`fetchSSE.ts` 从 `startsWith("[progress] ")` 切片改为 `JSON.parse` + discriminated union；`BiArea.tsx` 删除前端 `stripChartMarker` 逻辑；新增意图语义层的 thinking 展示
- **后端定义事件 DTO**：Java 侧用 record 定义事件 envelope 及各事件类型，替代字符串拼接

## Capabilities

### New Capabilities
- `sse-event-protocol`: 统一的 SSE 事件 envelope 定义，包含 `status`/`intent`/`chunk`/`result`/`error`/`done` 事件类型的 JSON schema 与 discriminated union 契约，供 Chat 和 BI 共用

### Modified Capabilities
- `bi-streaming`: 事件载荷从字符串前缀改为 JSON envelope；新增 `intent` 事件推送意图语义层；`<<CHART:>>` 标记改为后端内部处理，前端不再剥离
- `chat-streaming`: 从裸 `Flux<String>` 改为统一 envelope 的 `chunk` + `done` 事件

## Impact

- **后端**：`BiController.java`、`ChatController.java`、`ChatService.java`、`InsightGenerationService.java`（保留 `<<CHART:>>` 内部约定）；新增事件 envelope record 类
- **前端**：`fetchSSE.ts`（解析逻辑重写）、`BiArea.tsx`（删 `stripChartMarker`、新增 thinking 区渲染意图）、`ChatArea.tsx`（适配新事件类型）
- **API 变更** **BREAKING**：`POST /api/bi/query` 和 `GET /api/chat/stream/{credentialId}` 的 SSE data 字段从字符串/带前缀字符串改为 JSON 对象。前端旧版本不兼容（内部项目，不做版本协商）
- **依赖**：无需新增；Jackson 已有，前端原生 `JSON.parse` 即可
