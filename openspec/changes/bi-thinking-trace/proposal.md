## Why

`unified-sse-protocol` 引入了 `intent` 事件，把意图提取的**结果**（subject/metrics/dimensions/filters）暴露给前端，但用户仍然看不到 AI「在什么菜单里选的」「尝试了几次」「为什么挑了某些 metric」——思考过程是黑盒。意图提取阶段内部最多重试 2 次（`MAX_RETRIES=2`），失败原因和原始 LLM 输出目前只进日志；语义层（可用主题/指标/维度菜单）只拼进 system prompt，前端完全感知不到。需要把 BI 管线的每一步事件化，让用户像看 Claude/Cursor 的 thinking 那样看到 AI 走过的路径。

## What Changes

- **新增 `trace` SSE 事件类型**：在现有 `status`/`intent`/`chunk`/`result`/`error`/`done` 之外加一种 `type:"trace"` 事件，载荷为 `{ spanId, parentId?, name, status, attributes? }`。前端可忽略 trace 事件，老 UI 仍工作（向后兼容）
- **BI 管线每个阶段事件化**：`semantic-context` → `intent-extraction`（含每次 LLM call 子 span）→ `sql-build` → `query-execute` → `insight-generation` 五个阶段，长步骤发 start+end 双事件，短步骤只发 end
- **暴露语义层菜单**：`semantic-context` span 的 `attributes.subjects` 携带可用主题/指标/维度的**业务名 + 描述 + 聚合类型 + 维度类型**，**不**暴露 `column`、`table`、SQL 字符串（与 `unified-sse-protocol` 的「不暴露 SQL」non-goal 保持一致）
- **暴露意图提取重试过程**：每次 LLM attempt 是 `intent-extraction` 下的独立子 span，失败 attempt 在 end attributes 暴露 `error`（解析/校验错误）和 `rawOutput`（LLM 原始 JSON），重试 attempt 在 start attributes 暴露 `feedback` 文案
- **洞察生成阶段不暴露 rawOutput**：洞察文本已通过 `chunk` 事件流式推送，trace span 仅携带元信息（`chartType`），避免重复
- **前端 ThinkingBlock 重写为可折叠时间线**：内联在消息气泡中，流式过程中自动展开显示各 span 状态，完成后自动折叠成单行摘要，点击展开看 attributes

## Capabilities

### New Capabilities
<!-- 无新增 capability，trace 事件属于 bi-streaming 同一 SSE 流的扩展 -->

### Modified Capabilities
- `bi-streaming`: 新增 `trace` 事件类型与管线 span 化要求；新增语义层菜单暴露、意图提取重试可见性、ThinkingBlock 时间线渲染等场景

## Impact

- **后端** `services/ai4j-factory-service`：
  - 新增 `sse/TraceEvent.java` record；`sse/SseEvent.java` sealed permits 增加该类型
  - `bi/semantic/SemanticLayer.java` 新增 `toTracePayload()` 方法，输出脱敏后的主题/指标/维度结构（无 column/table）
  - `bi/intent/IntentExtractionService.java` 重构 `extractWithContext`：从单返回值改为可发射每次 attempt 的 trace span（引入回调或 Flux 返回类型，需在 design 中决定）
  - `bi/BiQueryWorkflowService.java` 在每个阶段包裹 span start/end 事件
  - 测试：`IntentExtractionServiceTest.java`、`BiControllerTest.java` 增补 trace 事件断言
- **前端** `apps/ai4j-factory-ui`：
  - `src/utils/sse.ts`：`SseEvent` discriminated union 增加 `trace` 类型；`SSECallbacks` 增加 `onTrace` 回调
  - `src/components/BiArea.tsx`：`BiMessage` 增加 `trace` 字段（span 树）；`ThinkingBlock` 重写为可折叠时间线，支持展开看 attributes
  - 测试：`sse.test.ts`、`BiArea.test.tsx` 覆盖 trace 事件解析与渲染
- **API 变更**：SSE 流新增 `trace` 事件类型，与现有事件并存，向后兼容（前端忽略 `trace` 仍可正常工作）
- **依赖**：无新增；Jackson 与原生 `JSON.parse` 即可
