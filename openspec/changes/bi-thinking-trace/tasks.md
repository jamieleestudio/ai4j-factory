## 1. 后端 SSE 事件基础设施

- [x] 1.1 新建 `services/ai4j-factory-service/src/main/java/org/ai4j/factory/sse/TraceEvent.java`：record，字段为 `spanId`、`parentId`（可空）、`name`、`status`（枚举 `START`/`END`）、`attributes`（`Map<String,Object>` 可空），实现 `SseEvent`，`type()` 返回 `"trace"`
- [x] 1.2 在 `SseEvent.java` sealed interface 的 `permits` 列表中追加 `TraceEvent`
- [x] 1.3 验证 `SseEventSerializer` 能正确序列化 `TraceEvent`（含 `type` 鉴别字段）；若序列化器依赖具体子类型枚举，补充 `TraceEvent` 分支
- [x] 1.4 新建 `TraceStatus` 枚举（`START`、`END`），放在 `org.ai4j.factory.sse` 包下

## 2. 语义层 trace 载荷

- [x] 2.1 新建 `services/ai4j-factory-service/src/main/java/org/ai4j/factory/bi/semantic/SubjectTracePayload.java`：record，字段为 `name`、`description`、`metrics`（`List<MetricTracePayload>`）、`dimensions`（`List<DimensionTracePayload>`）
- [x] 2.2 新建 `MetricTracePayload.java`：record，字段为 `name`、`description`、`aggregation`（`String`，已 `name()` 化）
- [x] 2.3 新建 `DimensionTracePayload.java`：record，字段为 `name`、`type`（`String`，已 `name()` 化）
- [x] 2.4 在 `SemanticLayer.java` 新增 `toTracePayload()` 方法，返回 `List<SubjectTracePayload>`：遍历 `subjectsByName`，对每个 `Subject` 构造 `SubjectTracePayload`，**只映射业务字段**（`name`/`description`/`aggregation`/`type`），不映射 `column`/`table`
- [x] 2.5 单元测试：`SemanticLayerTest`（新建或扩展现有测试）验证 `toTracePayload()` 输出无 `column`/`table` 字段

## 3. 意图提取 trace 发射

- [x] 3.1 在 `IntentExtractionService.extractWithContext` 签名中新增参数 `java.util.function.Consumer<TraceEvent> traceEmitter`，原 `extract(question, credentialId, modelName)` 重载方法内部传 `null` 或空 emitter
- [x] 3.2 在 `extractWithContext` 的重试循环开始前，发射 `intent-extraction` span 的 `START` 事件（生成 spanId，如 `"intent-extraction"`）
- [x] 3.3 每次 LLM call 前，发射 `llm-call` 子 span 的 `START` 事件，`parentId` 指向 `intent-extraction` 的 spanId，`attributes.attempt` = 当前 attempt 序号（从 1 开始）；重试时（attempt > 1）`attributes.feedback` 携带修正反馈文案
- [x] 3.4 LLM call 返回后，发射 `llm-call` 子 span 的 `END` 事件，`attributes.rawOutput` = LLM 原始响应字符串；若解析或校验失败，`attributes.error` 携带异常消息
- [x] 3.5 重试循环结束后（无论成功失败），发射 `intent-extraction` span 的 `END` 事件
- [x] 3.6 修改 `BiQueryWorkflowService.resolveIntent`：构造一个 `List<TraceEvent> traces` 收集器，传给 `extractWithContext` 作为 `Consumer`（`traces::add`），保留收集到的 trace 事件供后续拼接
- [x] 3.7 单元测试：`IntentExtractionServiceTest` 覆盖（a）单次成功、（b）首次失败重试成功、（c）全部失败三种场景下的 trace 事件序列

## 4. BiQueryWorkflowService 管线 span 编排

- [x] 4.1 在 `BiQueryWorkflowService.stream` 中，`Flux.concat` 第一个 `StatusEvent(analyzing)` 之后，新增 `semantic-context` trace `END` 事件，`attributes.subjects` = `semanticLayer.toTracePayload()`，`spanId` = `"semantic-context"`
- [x] 4.2 在 `resolveIntent` 返回后、`continueWorkflow` 内部：先把收集到的 `intent-extraction` trace 事件序列（START + 各 llm-call 子 span + END）作为 `Flux.just(...)` 拼接到流前面
- [x] 4.3 在 `queryAssemblyService.toIntentEvent(plan)` 之前推送收集到的 trace 事件，确保 trace 事件先于 `intent` 事件
- [x] 4.4 在 `queryExecutionService.execute` 调用前后，分别推送 `query-execute` span 的 `START` 和 `END` 事件，`END` 的 `attributes.rowCount` = `data.size()`，**不**含 SQL 字符串或 data preview
- [x] 4.5 在 `queryAssemblyService.assemble` 调用后，推送 `sql-build` span 的 `END` 事件（单事件，无 START），`attributes` 可仅含 `rowCount` 占位（实际 rowCount 在 query-execute 后才确定，可考虑省略或标记为 N/A）—— 决策：`sql-build` 不带 rowCount，仅作为阶段标记，让前端显示「SQL 构建完成」
- [x] 4.6 在 `insightGenerationService.generateStream` 调用前推送 `insight-generation` span 的 `START` 事件
- [x] 4.7 在 `insightStreamAssembler.assemble` 完成后、推送 `result` 事件前，推送 `insight-generation` span 的 `END` 事件，`attributes.chartType` = `extractChartType(fullText)`，**不**含 `rawOutput`
- [x] 4.8 在 `BiQueryWorkflowService` 注入 `SemanticLayer`（已有依赖），用于 `toTracePayload()`
- [x] 4.9 新增配置项 `bi.trace.enabled`（默认 `true`），在 `application.yml` 中声明；workflow service 读取此配置，若为 `false` 则跳过所有 trace 事件推送（保留作为回滚开关）
- [x] 4.10 集成测试：`BiControllerTest` 覆盖完整 BI 查询流程，断言 SSE 输出按序含 `semantic-context`/`intent-extraction`+`llm-call`子 span/`sql-build`/`query-execute`/`insight-generation` trace 事件

## 5. 前端 SSE 解析层

- [x] 5.1 在 `apps/ai4j-factory-ui/src/utils/sse.ts` 的 `SseEvent` discriminated union 中新增 `trace` 类型：`{ type: "trace"; spanId: string; parentId?: string; name: string; status: "START" | "END"; attributes?: Record<string, unknown> }`
- [x] 5.2 在 `SSECallbacks` 接口中新增 `onTrace?: (event: TraceEvent) => void` 可选回调
- [x] 5.3 在 `fetchSSE`（或 `subscribeSSE`）的事件分发 switch 中，新增 `case "trace"` 分支调用 `callbacks.onTrace`
- [x] 5.4 单元测试：`sse.test.ts` 覆盖 `trace` START 和 END 事件的解析、`onTrace` 回调被调用、未知 `name` 不影响分发

## 6. 前端 BiArea 状态管理

- [x] 6.1 在 `BiArea.tsx` 的 `BiMessage` assistant 类型中新增 `trace?: TraceSpan[]` 字段，`TraceSpan` 类型含 `spanId`、`parentId?`、`name`、`status`、`attributes?`、`children?: TraceSpan[]`（前端构建的 span 树）
- [x] 6.2 在 `handleQuery` 中新增 `onTrace` 回调：每收到一个 trace 事件，追加到对应 assistant message 的 `trace` 数组
- [x] 6.3 实现一个工具函数 `buildSpanTree(events: TraceEvent[]): TraceSpan[]`：按到达顺序构建 span 树，未闭合 span（有 START 无 END）保留为进行中态
- [x] 6.4 在 `onDone` 回调中，标记 trace 时间线为「已完成」，触发自动折叠逻辑
- [x] 6.5 单元测试：`BiArea.test.tsx` 覆盖 trace 事件累积、span 树构建、嵌套 span（llm-call 在 intent-extraction 下）的缩进渲染

## 7. 前端 ThinkingBlock 时间线渲染

- [x] 7.1 重写 `ThinkingBlock` 组件：接收 `trace: TraceSpan[]` 和 `progressText` props，渲染为可折叠时间线
- [x] 7.2 流式过程中（trace 数组非空且未收到 `done`）：默认展开，每个 span 一行，含图标（已结束 `●`、进行中 `●` 脉冲动画）、阶段名、耗时/状态摘要
- [x] 7.3 嵌套 span（有 `parentId`）渲染时缩进 1 级，让用户看出 `llm-call` 是 `intent-extraction` 的子步骤
- [x] 7.4 每个 span 行支持点击展开，显示 `attributes` 对象的所有字段（`rawOutput`、`error`、`feedback`、`attempt` 等），用 `<pre>` 或类似格式展示 JSON
- [x] 7.5 `done` 事件后：自动折叠为单行 `▶ 思考过程 · 3.2s · 4 步`，点击重新展开
- [x] 7.6 保留对原 `intent` 字段的展示（subject/metrics/dimensions/filters）—— 可以作为 `intent-extraction` span 的展开内容，或单独显示；决策：单独显示在时间线下方，保持向后兼容
- [x] 7.7 单元测试：覆盖（a）流式展开、（b）完成后折叠、（c）点击展开 attributes、（d）嵌套 span 缩进

## 8. 验证

- [x] 8.1 后端：运行 `mvn test`（或对应 Maven 命令），确认 `IntentExtractionServiceTest`、`BiControllerTest`、`SemanticLayerTest` 通过
- [ ] 8.2 后端：启动服务，用 `curl "http://localhost:8080/api/bi/query?question=华东区销售额&credentialId=1&modelName=..."` 请求，确认 SSE 输出按序含 `semantic-context`/`intent-extraction`/`llm-call`/`sql-build`/`query-execute`/`insight-generation` trace 事件，且 `semantic-context` 载荷无 `column`/`table`
- [x] 8.3 前端：运行 `pnpm test`（vitest），确认 `sse.test.ts` 与 `BiArea.test.tsx` 通过
- [ ] 8.4 前端：`pnpm dev` 启动，浏览器中发起 BI 查询，确认思考过程时间线流式展开、各 span 实时点亮、完成后自动折叠、点击 span 可看 attributes（含失败 attempt 的 rawOutput 和 error）
- [ ] 8.5 前端：模拟意图提取失败场景（如问一个不存在的 metric），确认重试 span 在时间线中可见，失败 attempt 的 `error` 和 `rawOutput` 可展开查看
- [ ] 8.6 端到端：在 `application.yml` 中设置 `bi.trace.enabled=false`，重启服务，确认前端仍正常工作（trace 事件不推送，老 UI 不受影响）
