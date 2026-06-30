## 1. Chat 后端流式修复

- [x] 1.1 `ChatClientFactory`: 在 `OpenAiChatOptions` 中显式开启 streaming（添加 `streamUsage` 等 streaming 相关配置）
- [x] 1.2 `ChatService`: 返回 `Flux<String>` 替代 `Flux<ChatResponse>`，在 service 层完成文本提取
- [x] 1.3 `ChatController`: 返回 `Flux<String>` 替代手动 `SseEmitter` + subscribe 模式

## 2. BI 后端流式改造

- [x] 2.1 `InsightGenerationService`: 新增 `generateStream()` 方法，使用 `.stream().chatResponse()` 替代 `.call().content()`
- [x] 2.2 `BiController`: 改为返回 `Flux<String>`，`produces = TEXT_EVENT_STREAM_VALUE`，拼接 `[progress]` + `[chunk]` + `[result]` 事件流
- [x] 2.3 `BiController`: 在意图提取、SQL执行前后插入 `[progress]` 进度事件

## 3. 前端 BI 流式适配

- [x] 3.1 新增 `src/utils/fetchSSE.ts`：封装 `fetch` + `ReadableStream` 解析 SSE 的工具函数
- [x] 3.2 `BiArea.tsx`: 替换 `fetch().json()` 为 `fetchSSE`，处理 `[progress]`、`[chunk]`、`[result]` 三种事件
- [x] 3.3 `BiArea.tsx`: 进度事件更新加载提示文字，chunk 事件逐字追加洞察文本，result 事件渲染数据表格

## 4. 验证

- [x] 4.1 手动测试 Chat 流式：发送消息后确认文本逐 token 出现
- [x] 4.2 手动测试 BI 流式：发送数据问题后确认进度提示 → 逐字洞察 → 表格渲染
