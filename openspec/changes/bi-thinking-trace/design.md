## Context

`unified-sse-protocol` 已将 BI SSE 流统一为 JSON envelope，事件类型为 `status`/`intent`/`chunk`/`result`/`error`/`done`。其中 `intent` 事件在意图提取**完成后**推送 `{ subject, metrics, dimensions, filters }`——只暴露 LLM 的最终选择，不暴露过程。

当前 BI 管线（`BiQueryWorkflowService.stream`）的实际执行步骤：

```
status(analyzing)
  → IntentExtractionService.extractWithContext()
      内部: buildSystemPrompt(semanticLayer.toPromptSummary() + 澄清上下文)
            call LLM (attempt 1)
            parse + validate
            失败 → 带 feedback 重试 (attempt 2, 最多 MAX_RETRIES=2 次)
            成功 → 返回 IntentExtractionResult.Ready
  → queryAssemblyService.assemble() (内部 getSubject + SqlBuilder.build)
  → intent 事件 + status(querying)
  → queryExecutionService.execute() (返回 List<Map<String,Object>>)
  → status(insight)
  → InsightGenerationService.generateStream() → chunk* → result → done
```

中间步骤完全不可见：语义层菜单（用户问「为什么是这两个 metric」无法回答）、重试过程（LLM 第一次走错了什么、为什么走错）、SQL 构建、查询行数预披露，全部只在后端日志里。

## Goals / Non-Goals

**Goals:**
- 在 BI SSE 流中新增 `trace` 事件类型，把管线每个阶段事件化为 span
- 暴露语义层菜单（业务名 + 描述 + 聚合类型 + 维度类型），让用户看到 LLM 在什么选项里选
- 暴露意图提取的每次 LLM attempt（成功和失败），含 rawOutput 和 error
- 前端 ThinkingBlock 渲染为可折叠时间线，完成后自动折叠
- 与现有 typed 事件并存，前端忽略 `trace` 仍可工作（向后兼容）

**Non-Goals:**
- **不替换**现有 `status`/`intent`/`chunk`/`result`/`error`/`done` 事件类型（避免再次 breaking change）
- **不暴露** SQL 字符串、物理 schema（`column`、`table`）——沿用 `unified-sse-protocol` 的 non-goal
- **不暴露** 查询结果 data preview（敏感行问题，留作后续 change）
- **不暴露** 洞察生成阶段的 `rawOutput`（`chunk` 事件已流式覆盖）
- **不暴露** LLM reasoning token（当前模型链路不带 thinking 字段）
- **不改** BI 执行管线本身（intent → SQL → execute → insight 四阶段不变）
- **不做** trace viewer 侧栏或独立 trace 页面（内联在消息气泡中即可）

## Decisions

### Decision 1: `trace` 事件与现有 typed 事件并存，不替换

```
data: {"type":"status", ...}     ← 保留
data: {"type":"trace",  ...}    ← 新增
data: {"type":"intent", ...}    ← 保留
data: {"type":"chunk",  ...}    ← 保留
data: {"type":"result", ...}    ← 保留
data: {"type":"error",  ...}    ← 保留
data: {"type":"done",   ...}    ← 保留
```

**Rationale:** `unified-sse-protocol` 刚刚（同周）做完一次 breaking change，把 BI 流从字符串前缀改成 JSON envelope。短期内再 breaking 一次成本高、收益低。`trace` 是叠加在 envelope 之上的可观测层，老前端忽略 `type:"trace"` 事件仍能渲染基础 UI。

**Alternatives considered:**
- *统一为 trace span，所有事件都是 span end*：契约更纯，但等于又一次 breaking change，且 `chunk` 流式场景下 span 粒度不对（一个洞察生成对应 N 个 chunk，span 是 1 个）。
- *用标准 SSE `event:` 字段区分*：Spring MVC 的 `Flux<ServerSentEvent>` 支持，但 `unified-sse-protocol` 已选定 `data:` + JSON + `type` 鉴别字段的方案，沿用更一致。

### Decision 2: TraceEvent record 形状

```java
public record TraceEvent(
    String spanId,           // 必填，唯一标识
    String parentId,         // 可选，根 span 为 null
    String name,             // "semantic-context" | "intent-extraction" | "llm-call" | "sql-build" | "query-execute" | "insight-generation"
    TraceStatus status,      // START | END
    Map<String, Object> attributes  // 可选，载荷
) implements SseEvent {
    @Override public String type() { return "trace"; }
}
```

序列化示例：
```json
{"type":"trace","spanId":"s2-a1","parentId":"s2","name":"llm-call","status":"START","attributes":{"attempt":1}}
{"type":"trace","spanId":"s2-a1","parentId":"s2","name":"llm-call","status":"END","attributes":{"rawOutput":"{...}","error":"Unknown metric 'sales'"}}
```

**Alternatives considered:**
- *用 `phase` 而非 `status`*：避免与现有 `StatusEvent` 字段名冲突，且 `status` 在 trace 语境（OpenTelemetry span status）是标准词。选 `status`。
- *span start 和 end 合并为单事件，带 `durationMs`*：长步骤（LLM call 几秒）期间用户看不到「正在做」，体验差。选双事件。

### Decision 3: 双事件用于长步骤，单 end 事件用于短步骤

| 阶段 | 长度典型值 | 事件数 |
|------|----------|--------|
| semantic-context | <5ms（内存读取） | 1（end only） |
| intent-extraction | 1-3s（含 LLM call） | 2（start+end） |
| llm-call（子 span） | 1-3s | 2（start+end） |
| sql-build | <5ms | 1（end only） |
| query-execute | 10-100ms | 2（start+end） |
| insight-generation | 3-10s（流式） | 2（start+end） |

**Rationale:** 用户能感知的等待（>100ms）才需要 start 事件让前端显示「正在做 X」。微秒级步骤发 start+end 是噪音。前端通过「有 start 无 end」识别 in-progress span。

### Decision 4: 语义层 `toTracePayload()` 只暴露业务字段

```java
public List<SubjectTracePayload> toTracePayload() {
    return subjectsByName.values().stream()
        .map(s -> new SubjectTracePayload(
            s.getName(),
            s.getDescription(),
            s.getMetrics().stream().map(m -> new MetricTracePayload(
                m.getName(), m.getDescription(), m.getAggregation().name()
            )).toList(),
            s.getDimensions().stream().map(d -> new DimensionTracePayload(
                d.getName(), d.getType().name()
            )).toList()
        )).toList();
}
```

**暴露字段：** subject.name/description, metric.name/description/aggregation, dimension.name/type
**不暴露字段：** subject.table, metric.column, dimension.column

**Rationale:** 用户问「为什么挑了销售额」时，需要看到 LLM 菜单里有「销售额（订单金额总和）」这样的业务描述。`column` 是物理 schema 细节，暴露后用户能反推表结构（与 SQL 不暴露的 non-goal 一致）。

**Alternatives considered:**
- *直接复用 `toPromptSummary()` 文本*：前端解析文本困难，且文本里有列名混合。结构化 JSON 更好。
- *暴露全部 Subject 对象（含 column）*：违反 non-goal，否决。

### Decision 5: 意图提取重试作为 `llm-call` 子 span

`IntentExtractionService.extractWithContext` 当前签名：
```java
public IntentExtractionResult extractWithContext(String question, PendingClarification context,
                                                  Long credentialId, String modelName)
```

返回单个 `IntentExtractionResult`，内部重试循环对调用方不可见。需要改造为发射每次 attempt 的 trace span。

**方案选择：传入 `Consumer<TraceEvent>` trace emitter 回调**

```java
public IntentExtractionResult extractWithContext(
    String question, PendingClarification context,
    Long credentialId, String modelName,
    Consumer<TraceEvent> traceEmitter  // 新增
)
```

每次 LLM call 前后调用 `traceEmitter.accept(startSpan)` / `traceEmitter.accept(endSpan)`。

**Alternatives considered:**
- *返回 `Flux<TraceEvent>` + 最终 result*：反应式签名改动大，且 `BiQueryWorkflowService` 的 `Mono.fromCallable` 模型不匹配。回调方案侵入小。
- *在 `IntentExtractionService` 内部直接发 SSE*：违反分层，service 不应感知 SSE 协议。回调解耦。

`BiQueryWorkflowService` 在调用 `extractWithContext` 时传入一个收集 trace 事件的 `Consumer`，然后在 `flatMapMany` 里把收集到的事件拼接到流中。具体收集机制：

```java
private Flux<SseEvent> continueWorkflow(BiQueryRequest request, IntentStage stage) {
    List<TraceEvent> traces = new ArrayList<>();
    IntentExtractionResult result = intentExtractionService.extractWithContext(
        ..., traces::add
    );
    // 把 traces 拼到 Flux 前面，然后是 intent 事件、status、查询、洞察...
}
```

注意：`extractWithContext` 仍是阻塞调用（在 `Mono.fromCallable` + `boundedElastic` 上跑），trace 事件在调用返回后一次性发出（不是真流式）。用户感知是「intent-extraction 阶段结束后，trace 时间线一次性铺开」。这是可接受的折中——LLM call 本身是阻塞的，无法在调用中途发 trace。重试发生在同一阻塞调用内，所以也是结束后才知道。

**如果未来要真正流式**（在 LLM call 进行中就发 start span），需要把 `IntentExtractionService` 改为反应式，但代价远大于收益，暂不做。

### Decision 6: 失败 attempt 在 end attributes 暴露 error 与 rawOutput

```json
{"type":"trace","spanId":"s2-a1","parentId":"s2","name":"llm-call","status":"END",
 "attributes":{
   "attempt": 1,
   "rawOutput": "```json\n{ \"subject\": \"订单分析\", \"metrics\": [\"sales\"] }\n```",
   "error": "Unknown metric 'sales'. Available: [销售额, 订单量, 平均客单价]"
 }}
```

成功 attempt 的 end attributes 仅含 `rawOutput` 和 `attempt`，不含 `error`。重试 attempt 的 start attributes 含 `feedback`（如「输出未通过解析或校验，请严格修正」）和 `attempt`。

### Decision 7: 前端 ThinkingBlock 重写为可折叠时间线

```
┌─ 消息气泡 ──────────────────────────────────────────────┐
│  ▼ 思考过程                       3.2s · 4 步          │  ← 默认展开（流式中）
│  ┌──────────────────────────────────────────────────┐  │
│  │  ● 语义层已加载                3ms                │  │
│  │    1 个主题 · 3 个指标 · 4 个维度                │  │
│  │    [展开]                                         │  │
│  │                                                   │  │
│  │  ● 意图提取                     2.1s · 2 次尝试   │  │
│  │    ✓ Subject: 订单分析                           │  │
│  │    ✓ Metrics: 销售额, 订单量                      │  │
│  │    ✓ Dimensions: 区域                             │  │
│  │    ✓ Filters: 区域 = 华东                        │  │
│  │    [展开看 LLM 原始输出 + 失败原因]                │  │
│  │                                                   │  │
│  │  ● SQL 构建                      3ms              │  │
│  │  ● 查询执行                      45ms · 42 行      │  │
│  │  ● 洞察生成                      1.0s · 流式       │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  华东区销售额为 1.2M...       ← 主答案                  │
└─────────────────────────────────────────────────────────┘
```

- 流式过程中：默认展开，每个 span 实时点亮
- `done` 事件后：自动折叠为单行 `▶ 思考过程 · 3.2s · 4 步`
- 点击折叠头：再次展开
- 点击单个 span：展开看 attributes（rawOutput、error 等）
- 没有 `parentId` 的 span 是顶层；有 `parentId` 的缩进显示（如 `llm-call` 缩进在 `intent-extraction` 下）

**Alternatives considered:**
- *侧栏 trace viewer（Langfuse 风格）*：BI 是给业务用户用的，不是开发者调试，侧栏过重。
- *平铺列表（无嵌套）*：丢失 retry 和 intent-extraction 的归属关系，用户看不出「这是重试」。
- *默认折叠*：流式时折叠用户看不到进度，体验差。

### Decision 8: span ID 生成策略

后端用 `AtomicLong` 递增，每个 BI 请求内独立计数（`s1`、`s2`、`s2-a1`、`s2-a2`、`s3`...）。不跨请求复用，无需持久化。

**Alternatives considered:**
- *UUID*：调试时人眼难读，前端日志也不直观。`s2-a1` 比 `550e8400-e29b-...` 友好。
- *OpenTelemetry span ID（16 hex）*：标准格式但与本项目其他 ID 风格不符。

## Risks / Trade-offs

- **[trace 事件流膨胀]** → semantic-context 在多 subject 场景下可能数 KB。SQL execute 的 rowCount 仅几十字节。整体每次 BI 查询增加约 2-5KB SSE 流量。可接受（chunk 流本身可能数十 KB）。未来若语义层巨大，可加分页或抽样。
- **[trace 阻塞发射]** → 由于 `IntentExtractionService.extractWithContext` 是阻塞调用，重试 trace 在调用返回后才一次性发出，用户感知是「intent-extraction 结束后时间线突然铺开」而非「实时点亮」。可接受，因为重试本身就在阻塞调用内发生。
- **[rawOutput 暴露 LLM 输出]** → 失败 attempt 的 rawOutput 可能含 LLM 幻觉内容（编造的 metric 名、错误的 JSON）。这是特性而非 bug——让用户看到 LLM 走错了什么。但需注意 rawOutput 不含敏感数据（system prompt 不含密钥），可安全暴露。
- **[前端忽略 trace 仍工作]** → 这是 backward compat 的核心保证。`fetchSSE.ts` 在 `switch(type)` 中对 `trace` 走默认分支（忽略），不影响 status/intent/chunk/result/done 的处理。
- **[span ID 跨请求冲突]** → span ID 仅在单次 BI 请求内有意义，前端不持久化、不跨请求比较。无冲突风险。
- **[trace 与 status 时序]** → `intent-extraction` span 的 start 应在 `status(analyzing)` 之后、第一次 `llm-call` start 之前。end 应在 `intent` 事件之前。前端按 SSE 到达顺序处理，无需排序。

## Migration Plan

无版本协商（内部项目），一次部署完成。前后端同仓库同步发布。

**回滚策略：** 若 trace 事件导致前端解析问题，后端可通过配置开关 `bi.trace.enabled=false` 关闭 trace 发射（默认开）。前端对 `trace` 事件走默认忽略分支，无需回滚。

## Open Questions

- **trace 事件是否需要 `startedAt` / `endedAt` 时间戳？** 当前决策是不带——前端用 SSE 到达时间近似。如果未来要做性能分析（哪一步慢），需要补。暂不做。
- **`Consumer<TraceEvent>` 回调是否够用？** 若 `IntentExtractionService` 未来要做更复杂的反应式重试（如指数退避），回调方案可能不够，需改 Flux。当前 MAX_RETRIES=2 固定重试，回调足够。
