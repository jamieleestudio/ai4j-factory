## 1. 前置确认

- [x] 1.1 确认 `unified-sse-protocol` change 已归档（`openspec/specs/sse-event-protocol/spec.md` 存在，基础事件 envelope spec 已建立）
- [x] 1.2 确认 `BiController` 已采用统一 SSE envelope 推送（`status`/`intent`/`chunk`/`result`/`done` 事件均为 JSON 对象），clarification 在此基础上扩展

## 2. 后端：意图提取双状态协议

- [x] 2.1 新增 `IntentExtractionResult` 类型（sealed interface 或 record），承载 LLM 双状态输出：`Ready(subject, metrics, dimensions, filters)` 与 `NeedsClarification(reason, message, options)`
- [x] 2.2 重写 `IntentExtractionService.buildExtractionPrompt()`：Prompt 让 LLM 二选一输出 `{"status":"ready",...}` 或 `{"status":"needs_clarification","reason","message","options"}`，含澄清触发示例
- [x] 2.3 更新 `parseResponse()` 解析双状态 JSON 为 `IntentExtractionResult`
- [x] 2.4 删除 `IntentExtractionService.validate()` line 146-148 空 metrics 兜底逻辑（`if (intent.getMetrics().isEmpty()) intent.getMetrics().add(...)`）
- [x] 2.5 兜底：若 LLM 返回 `ready` 但 metrics 为空且主题有可用指标，转为 `metric_unspecified` 澄清（不偷塞指标，不执行 SQL）
- [x] 2.6 新增 `extractWithContext(question, sessionId, context)` 方法：把 `originalQuestion`、`options`、用户本次输入作为上下文构造 prompt

## 3. 后端：ClarificationStore 会话存储

- [x] 3.1 新增 `PendingClarification` record：`{originalQuestion, options, selectedValue, createdAt}`
- [x] 3.2 新增 `ClarificationStore` 类（in-memory，基于 `ConcurrentHashMap`），存储 `sessionId → PendingClarification`
- [x] 3.3 实现 TTL 5 分钟自动清理（`ScheduledExecutorService` 定时扫描或惰性过期检查）
- [x] 3.4 容量上限 N 条（建议 1000，防内存膨胀），超出时淘汰最旧条目

## 4. 后端：ClarificationEvent SSE DTO

- [x] 4.1 新增 `ClarificationOption` record：`{label, value, description}`
- [x] 4.2 新增 `ClarificationEvent` record 实现 `SseEvent`：`{sessionId, message, options}`
- [x] 4.3 确认 `SseEventSerializer` 能序列化 `ClarificationEvent`（若序列化器用 type discriminator 自动派发则无需改动）

## 5. 后端：BiController 澄清分支集成

- [x] 5.1 `QueryRequest` record 新增可选 `sessionId` 字段
- [x] 5.2 意图提取后判定 `IntentExtractionResult` 状态：`ready` 走原流程，`needs_clarification` 走澄清分支
- [x] 5.3 澄清分支：生成 `sessionId`（UUID），存入 `ClarificationStore`，从 `SemanticLayer` 补全 options 的 `description`，推送 `ClarificationEvent`，推送 `done`，提前结束流（不执行 SQL 和洞察生成）
- [x] 5.4 接收 `sessionId` 时：从 `ClarificationStore` 取 pending clarification，调 `IntentExtractionService.extractWithContext()`；`sessionId` 不存在时回退全新查询（不带上下文）
- [x] 5.5 options 构造逻辑按 `reason` 分类：`question_unclear` 列出全部主题、`subject_ambiguous` 列出含指标的主题、`metric_unspecified` 列出该主题的指标

## 6. 前端：fetchSSE 解析 clarification 事件

- [x] 6.1 新增 `ClarificationOption` 类型：`{label: string, value: string, description?: string}`
- [x] 6.2 `SseEvent` discriminated union 新增 clarification 分支：`{type:"clarification", sessionId: string, message: string, options: ClarificationOption[]}`
- [x] 6.3 `fetchSSE` 回调接口新增 `onClarification` 回调
- [x] 6.4 解析逻辑按 `type` 分发到 `onClarification`

## 7. 前端：BiArea clarification 渲染态

- [x] 7.1 `BiMessage` 联合类型新增 clarification 状态：`{status:"clarification", sessionId: string, message: string, options: ClarificationOption[]}`
- [x] 7.2 新增 `ClarificationBlock` 组件：渲染提示文案 + options chips（含 label + description）
- [x] 7.3 chip 点击：把 `option.value` 作为 question + `sessionId` 调 `handleQuery`
- [x] 7.4 在 BiMessage 状态中持有 `sessionId`，`handleQuery` 接收可选 `sessionId` 参数传给 `fetchSSE`
- [x] 7.5 clarification 状态下 `ChatInput` 仍可输入（用户可重新打字，携带当前 `sessionId`）

## 8. 端到端测试

- [x] 8.1 后端单测：`IntentExtractionService` 双状态解析（`ready` / `needs_clarification` 三种 reason）
- [x] 8.2 后端单测：`ClarificationStore` TTL 过期清理与容量上限淘汰
- [x] 8.3 后端单测：`BiController` 澄清分支（LLM mock 返回 `needs_clarification`，验证推送 `clarification` 事件 + `done`、不推送 `intent`/`chunk`/`result`）
- [x] 8.4 后端单测：`sessionId` 不存在时回退全新查询（不带上下文调 LLM）
- [x] 8.5 后端单测：LLM 返回 `ready` 但 metrics 为空时兜底转 `metric_unspecified` 澄清
- [x] 8.6 前端单测：`fetchSSE` 解析 `clarification` 事件并触发 `onClarification`（测试代码已写入 `fetchSSE.test.ts`，tsc 类型检查通过；vitest 运行器有 Vite 7 ESM 兼容问题待修）
- [x] 8.7 前端单测：`BiArea` chip 点击携带 `sessionId` 调 `handleQuery`（测试代码已写入 `BiArea.test.tsx`，tsc 类型检查通过；vitest 运行器有 Vite 7 ESM 兼容问题待修）
- [ ] 8.8 E2E：输入 "1" → 验证触发 `question_unclear` 列出所有主题 → 点 "订单分析" chip → 验证触发 `metric_unspecified` 列出指标 → 点 "销售额" → 验证正常执行查询返回结果
- [ ] 8.9 E2E：输入 "销售额" → 验证触发 `subject_ambiguous` 列出含该指标的主题
- [ ] 8.10 E2E：`sessionId` 过期后点 chip → 验证回退全新查询（前端收到 sessionId not found 错误时重新走 LLM 提取）
