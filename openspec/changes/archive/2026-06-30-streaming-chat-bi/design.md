## Context

当前项目中 Chat 和 BI 的返回方式：Chat 在架构上使用了 SSE（`SseEmitter` + `Flux<ChatResponse>` + `EventSource`），但 `OpenAiChatModel` 未正确配置 streaming，实际效果是一次性返回全部文本。BI 则是完全同步执行，`BiController.query()` 顺序执行意图提取 → SQL生成 → SQL执行 → 洞察生成，最后返回完整 JSON。

目标：两个功能都实现真正的逐 token 流式输出，让用户看到 AI 逐字生成内容。

## Goals / Non-Goals

**Goals:**
- Chat：修复 `OpenAiChatModel` 的 streaming 配置，简化 SSE 实现（`Flux<String>` 替代手动 `SseEmitter`），实现逐 token 输出
- BI：将同步返回改为 SSE 流式返回，前三个步骤推送进度事件，最后的洞察生成逐 token 输出，结尾推送结构化数据
- 前端 BI：从 `fetch().json()` 改为 `fetch` + `ReadableStream` 解析 SSE 流

**Non-Goals:**
- 不改变 Chat 的前端 EventSource 方案（已经是 SSE 接收方式）
- 不改变意图提取的同步方式（这是结构化 JSON 提取，流式无意义）
- 不添加 WebFlux 依赖（Spring MVC 原生支持 `Flux` 返回值 + `text/event-stream`）
- 不改变 AI 配置的 DB 驱动方式

## Decisions

### 1. Chat: `Flux<String>` 替代 `SseEmitter`

**选择**: Controller 直接返回 `Flux<String>`，配合 `produces = TEXT_EVENT_STREAM_VALUE`

**理由**: Spring MVC 6.x 原生支持 `Flux` 作为返回值，每个元素自动序列化为 SSE `data:` 事件，无需手动管理 `SseEmitter` 的生命周期。代码更简洁，且每个元素自动 flush。

**替代方案**: 保留 `SseEmitter` 仅修复 `OpenAiChatOptions`。但 `SseEmitter` 在高频 `send()` 场景下可能合并事件，不如 `Flux` 的响应式背压模型可靠。

### 2. Chat: 修复 `OpenAiChatOptions` streaming

**选择**: 在 `ChatClientFactory` 中通过 `OpenAiChatOptions` 显式配置 streaming 相关参数

**理由**: 当前 `OpenAiChatOptions` 只设了 `model` 和 `temperature`。虽然 `ChatClient.prompt().stream()` 应该自动开启 streaming，但手动构建的 `OpenAiChatModel` 可能不会正确传递 `stream: true` 给底层 API。显式配置确保 LLM API 返回 SSE 流。

### 3. BI: SSE 事件类型约定

**选择**: 使用三种事件类型区分不同阶段

```
[progress]  - 进度提示文字，前端显示为状态信息
[chunk]     - 洞察文本片段，前端逐字追加到 AI 消息
[result]    - 结构化结果 JSON（chartType + data），前端渲染表格
```

**理由**: 简单的前缀标记方案，无需复杂的 SSE `event:` 字段解析。前端通过字符串前缀即可路由到不同处理逻辑。

**替代方案**: 使用标准 SSE `event:` 字段区分事件类型。但 `EventSource` API 的 `addEventListener` 配合 `ReadableStream` 手动解析时，前缀匹配更简单直接。

### 4. BI: 前面步骤静默 + 最后一步流式

**选择**: 意图提取、SQL生成、SQL执行 三个步骤静默执行（通过 `[progress]` 事件告知进度），仅洞察生成步骤进行逐 token 流式输出

**理由**:
- 意图提取返回结构化 JSON（QueryIntent），流式无意义
- SQL生成和SQL执行是纯后端计算，无 LLM 调用
- 洞察生成是 LLM 调用，输出自然语言，是用户想看到逐字生成的部分

### 5. 前端 BI: `fetch` + `ReadableStream` 替代 `EventSource`

**选择**: 使用 `fetch` 的 `response.body.getReader()` 手动读取 SSE 流

**理由**: BI 端点使用 POST 方法（需要 JSON body 传递 question），而 `EventSource` 仅支持 GET。`fetch` + `ReadableStream` 支持 POST 且提供更灵活的控制。

## Risks / Trade-offs

- **[风险] `Flux<String>` 返回值可能不被 Spring MVC 正确序列化为 SSE** → 已验证 Spring MVC 6.x 支持此模式；如遇问题可退回 `SseEmitter`
- **[风险] 前端 `ReadableStream` 手动解析 SSE 比 `EventSource` 更复杂** → 封装一个 `fetchSSE` 工具函数，统一处理解析和重连逻辑
- **[风险] BI 流式期间用户刷新页面会丢失查询** → 当前 scope 不做会话持久化，列为后续优化项
- **[权衡] BI 前面步骤进度提示 vs 静默等待** → 进度提示提供更好的体验，但增加了少量代码复杂度
