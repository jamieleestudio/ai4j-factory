## 1. 后端事件 envelope DTO

- [x] 1.1 新建 `SseEvent` sealed interface 及 `type()` 方法，位于 `org.ai4j.factory.sse` 包
- [x] 1.2 新建 `StatusEvent` record（`stage`, `message`），实现 `SseEvent`，`type()` 返回 `"status"`
- [x] 1.3 新建 `IntentEvent` record（`subject`, `metrics`, `dimensions`, `filters`），实现 `SseEvent`
- [x] 1.4 新建 `ChunkEvent` record（`content`），实现 `SseEvent`
- [x] 1.5 新建 `ResultEvent` record（`chartType`, `data`, `rowCount`），实现 `SseEvent`
- [x] 1.6 新建 `ErrorEvent` record（`message`），实现 `SseEvent`
- [x] 1.7 新建 `DoneEvent` record（无字段），实现 `SseEvent`
- [x] 1.8 新建 `SseEventSerializer` 工具类，用 `ObjectMapper` 将 `SseEvent` 序列化为 JSON 字符串（含 `type` 字段）

## 2. 后端 BI Controller 改造

- [x] 2.1 `BiController.query()` 将 `sink.next("[progress] " + msg)` 改为推送 `StatusEvent` 序列化后的 JSON
- [x] 2.2 在 `intentService.extract()` 返回后、推送 querying `status` 前，构造并推送 `IntentEvent`（从 `QueryIntent` 透传字段）
- [x] 2.3 `insightService.generateStream(...).doOnNext` 中，对每个 chunk 先调 `InsightGenerationService.stripChartMarker` 再构造 `ChunkEvent` 推送
- [x] 2.4 `doOnComplete` 中构造 `ResultEvent`（`chartType` 来自 `extractChartType(fullText)`，`data` 为查询结果，`rowCount` 为 `data.size()`），推送后继续推送 `DoneEvent`
- [x] 2.5 `catch` 块改为推送 `ErrorEvent` 后推送 `DoneEvent`，再 `sink.complete()`
- [x] 2.6 删除 `buildResultJson` 私有方法（被 `ResultEvent` DTO + 序列化取代）

## 3. 后端 Chat 改造

- [x] 3.1 `ChatService.streamChat` 将 `chatClient.prompt().user(message).stream().content()` 的 `Flux<String>` 映射为 `Flux<String>`（每个 token 序列化为 `ChunkEvent` JSON）
- [x] 3.2 在 Flux 末尾追加 `DoneEvent` JSON（`concatWith(Flux.just(doneJson))` 或 `doOnComplete` 在响应式链路外推送）
- [x] 3.3 错误路径映射为 `ErrorEvent` JSON 后再 `done`（`onErrorResume` 返回 `Flux.just(errorJson, doneJson)`）

## 4. 前端 SSE 解析层

- [x] 4.1 在 `fetchSSE.ts` 定义 `SseEvent` discriminated union 类型（6 种事件 + 字段）
- [x] 4.2 `parseSSELine` 改为 `JSON.parse`，返回 `{ type, ...payload } | null`；非法 JSON 返回 `null`
- [x] 4.3 `SSECallbacks` 接口扩展为 `onStatus` / `onIntent` / `onChunk` / `onResult` / `onError` / `onDone`（保留旧名兼容或全量改名）
- [x] 4.4 `fetchSSE` 按 `type` 分发到对应回调；未知 `type` 跳过
- [x] 4.5 更新 `fetchSSE.test.ts` 覆盖 6 种事件的解析与分发

## 5. 前端 BiArea 适配

- [x] 5.1 `BiMessage` assistant 类型新增 `intent?: { subject, metrics, dimensions, filters }` 字段
- [x] 5.2 `handleQuery` 的 `onProgress` 回调改名为 `onStatus`，写入 `progressText`
- [x] 5.3 新增 `onIntent` 回调，将意图写入 assistant message 的 `intent` 字段
- [x] 5.4 `onChunk` 中删除 `stripChartMarker(fullText)` 调用（后端已剥离），直接累加 `fullText`
- [x] 5.5 `onResult` 解析 `chartType` / `data` / `rowCount`，移除对 `parsed.chartType` 的 fallback 默认值（后端已保证提供）
- [x] 5.6 新增 `onDone` 回调，设置 `isLoading = false`（原 `onDone` 逻辑迁移）
- [x] 5.7 在 streaming / success 渲染分支新增 thinking 区，展示 `status` 进度文案 + `intent` 语义层
- [x] 5.8 更新 `BiArea.test.tsx` 覆盖 intent 事件渲染、chunk 不含 `<<CHART:>>`

## 6. 前端 ChatArea 适配

- [x] 6.1 `ChatArea` 的 SSE 回调适配新接口（`onChunk` 写入 token，`onDone` 结束加载，`onError` 显示错误）
- [x] 6.2 移除对裸字符串 token 的处理逻辑（改为从 `chunk.content` 取值）

## 7. 验证

- [ ] 7.1 后端：启动服务，用 `curl` 请求 `POST /api/bi/query`，确认 SSE 输出为 JSON envelope 序列，含 `status`/`intent`/`chunk`/`result`/`done`
- [ ] 7.2 后端：用 `curl` 请求 `GET /api/chat/stream/{id}?message=...`，确认输出 `chunk` + `done` JSON
- [x] 7.3 后端：运行 `ChatControllerTest` 及新增的 BI controller 测试（若有）
- [x] 7.4 前端：运行 `pnpm test`（vitest），确认 `fetchSSE.test.ts` 与 `BiArea.test.tsx` 通过
- [ ] 7.5 前端：`pnpm dev` 启动，浏览器中发起 BI 查询，确认 thinking 区显示意图语义层、洞察逐字流出、无 `<<CHART:>>` 残留
- [ ] 7.6 前端：浏览器中发起 Chat 对话，确认逐字流式 + 正常结束
