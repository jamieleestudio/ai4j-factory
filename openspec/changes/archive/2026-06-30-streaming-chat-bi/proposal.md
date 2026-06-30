## Why

Chat 和 BI 目前返回结果都是一次性显示的——用户提交请求后需要等待数秒，然后结果一下子全部出现。虽然 Chat 在架构上使用了 SSE，但 LLM API 调用未正确开启 streaming 模式，导致 Flux 只发射单个元素，体验等同于一次返回。BI 则是完全同步执行（意图提取 → SQL → 洞察生成），用户在整个等待期间看不到任何反馈。两个功能都需要改为真正的逐 token 流式输出，让用户能实时看到 AI 的思考过程。

## What Changes

- **修复 Chat 流式输出**：`ChatClientFactory` 显式开启 streaming 配置；`ChatController` 从手动 `SseEmitter` 改为直接返回 `Flux<String>`；`ChatService` 返回 `Flux<String>` 而非 `Flux<ChatResponse>`
- **BI 改为 SSE 流式输出**：`BiController` 从返回 `InsightResponse` JSON 改为返回 SSE 流；前面步骤（意图提取、SQL生成、SQL执行）静默执行并推送进度事件；最后的洞察生成逐 token 流式输出；结尾推送结构化数据（表格 + 图表类型）
- **前端 BI 适配流式**：`BiArea` 从 `fetch().json()` 改为 `fetch` + `ReadableStream` 读取 SSE；解析 `[progress]`、`[chunk]`、`[result]` 三种事件类型分别处理

## Capabilities

### New Capabilities

- `chat-streaming`: Chat 真正的逐 token 流式输出，修复 Spring AI `OpenAiChatOptions` streaming 配置，简化 SSE 实现
- `bi-streaming`: BI 查询的 SSE 流式响应，包含进度事件、洞察文本逐字输出、结构化结果数据

### Modified Capabilities

<!-- No existing specs to modify -->

## Impact

- **后端**：`ChatClientFactory.java`、`ChatService.java`、`ChatController.java`、`BiController.java`、`InsightGenerationService.java`
- **前端**：`ChatArea.tsx`（可能需要微调）、`BiArea.tsx`（重大改动）
- **依赖**：无需新增；Spring AI 和 OpenAI SDK 已有 streaming 能力，只需正确配置
- **API 变更**：Chat 端点不变；BI 端点从 `POST /api/bi/query` 返回 JSON 改为返回 `text/event-stream`
