## Context

当前 SSE 返回数据的契约散落在字符串拼接代码里：

- **BI 端**（`BiController.java`）：用 `[progress]`/`[chunk]`/`[result]`/`[error]` 字符串前缀区分事件类型；图表类型 `<<CHART:bar>>` 让 LLM 在自然语言末尾输出，后端 `extractChartType` 正则抠取，前端 `stripChartMarker` 二次删除。意图提取结果（`QueryIntent`）只进日志，前端完全看不到。
- **Chat 端**（`ChatService.java`）：直接返回 `chatClient.prompt().user(message).stream().content()`，裸 `Flux<String>`，无事件类型、无 done 信号。
- **前端**（`fetchSSE.ts`）：`parseSSELine` 用 `startsWith("[progress] ")` 切片硬编码偏移量；`BiArea.tsx` 维护 `fullText` 并在前端剥离 `<<CHART:>>`。

两端契约没有 DTO，Java 和 TS 各自维护一套魔法字符串，必须人肉对齐。

## Goals / Non-Goals

**Goals:**
- 定义统一的 SSE 事件 envelope（JSON + `type` 鉴别字段），Chat 和 BI 共用
- BI 新增 `intent` 事件，把意图语义层（subject/metrics/dimensions/filters）作为 thinking 暴露给前端
- `<<CHART:>>` 标记退化为后端内部约定，前端永远看不到完整标记
- 后端用 record 定义事件类型，前端用 discriminated union，契约显式化

**Non-Goals:**
- 不暴露 SQL（保持内部，仅进日志）
- 不做协议版本协商 / 握手（内部项目，breaking change 直接替换）
- 不暴露 LLM reasoning token（当前模型链路未涉及 thinking 字段）
- 不改 BI 的执行管线本身（意图提取 → SQL → 查询 → 洞察的步骤不变）
- 不改 Chat 的功能集（不加 status/intent/result，Chat 保持极简）

## Decisions

### Decision 1: 事件 envelope 用 JSON + `type` 鉴别字段

每个 SSE `data:` 行是一个 JSON 对象，`type` 字段区分事件类型。

```
data: {"type":"status","stage":"analyzing","message":"正在分析你的问题..."}
data: {"type":"intent","subject":"orders","metrics":["sales_amount"],"dimensions":["region"],"filters":[]}
data: {"type":"chunk","content":"华东区"}
data: {"type":"result","chartType":"bar","data":[...],"rowCount":42}
data: {"type":"error","message":"..."}
data: {"type":"done"}
```

**Alternatives considered:**
- *SSE `event:` 字段*：标准 SSE 支持 `event: progress\ndata: ...`。但 Spring MVC 的 `Flux<String>` + `text/event-stream` 默认只发 `data:` 行，用 `event:` 需要返回 `ServerSentEvent` 类型或手动拼接，复杂度更高。统一用 `data:` + JSON 更简单，前端 `getReader()` 也只需解析 `data:` 行。
- *保留字符串前缀*：现状方案，魔法字符串难维护，无法承载结构化 payload（如 intent）。

### Decision 2: 事件类型集合

| type     | 用途                          | Chat | BI |
|----------|-------------------------------|------|----|
| status   | 进度文案                      | ✗    | ✓  |
| intent   | 意图语义层（thinking）        | ✗    | ✓  |
| chunk    | 文本 token                    | ✓    | ✓  |
| result   | 结构化结果（窄口径）          | ✗    | ✓  |
| error    | 错误                          | ✓    | ✓  |
| done     | 流结束信号                    | ✓    | ✓  |

**窄口径 result**：`{ chartType, data, rowCount }`。意图由 `intent` 事件单独推送，SQL 不暴露。把 intent 放进 result 还是单独事件——选单独事件，因为意图在管线早期就确定，前端可以早渲染 thinking，不必等洞察流完。

### Decision 3: `<<CHART:>>` 保留为后端内部约定

LLM prompt 不变（仍要求末尾输出 `<<CHART:bar>>`）。改动点：

- 后端在 `BiController` 累积 chunk 时，对每个 chunk 先调 `stripChartMarker` 再推送 `chunk` 事件给前端。前端收到的 chunk 永远不含标记。
- 洞察流完后，后端从完整 `fullText` 调 `extractChartType` 得到 `chartType`，放进 `result` 事件。

**Alternative considered**: 让 LLM 直接输出 JSON 结构化结果。但 streaming 模式下 JSON 边生成边解析很脆弱，自然语言 + 末尾标记的方案更稳。保留现状的 LLM 契约，只改传输层。

### Decision 4: 后端事件 DTO 用 sealed/record

Java 侧定义事件 envelope：

```java
public sealed interface SseEvent permits StatusEvent, IntentEvent, ChunkEvent, ResultEvent, ErrorEvent, DoneEvent {
    String type();
}
public record StatusEvent(String stage, String message) implements SseEvent { ... }
public record IntentEvent(String subject, List<String> metrics, List<String> dimensions, List<Map<String,Object>> filters) implements SseEvent { ... }
// ...
```

`BiController` 和 `ChatService` 用 `ObjectMapper` 序列化为 JSON 字符串后 `sink.next(json)`。

### Decision 5: 前端 discriminated union

```ts
type SseEvent =
  | { type: "status"; stage: string; message: string }
  | { type: "intent"; subject: string; metrics: string[]; dimensions: string[]; filters: unknown[] }
  | { type: "chunk"; content: string }
  | { type: "result"; chartType: string; data: Record<string, unknown>[]; rowCount: number }
  | { type: "error"; message: string }
  | { type: "done" };
```

`fetchSSE.ts` 的 `parseSSELine` 改为 `JSON.parse` + 按 `type` 分发；callbacks 从 4 个（onProgress/onChunk/onResult/onError）扩展为 6 个（加 onIntent/onDone）。

### Decision 6: `intent` 事件从 `QueryIntent` 透传

`IntentExtractionService.extract()` 已返回 `QueryIntent`。`BiController` 在拿到 intent 后、推送 `status`（querying）之前，推送 `intent` 事件。`QueryIntent` 的字段（getSubject/getMetrics/getDimensions/getFilters）直接映射到 `IntentEvent` record。

## Risks / Trade-offs

- **[Breaking change 无版本协商]** → 内部项目，前端与后端同仓库同步部署。改完一次性发，不做灰度。若未来需要多端版本，再补 `hello`/`version` 握手。
- **[JSON 序列化开销]** → 每个 chunk 都序列化 `{"type":"chunk","content":"..."}` 比裸字符串多了 ~20 字节。token 级 streaming 下可接受；若性能敏感，可后续优化为 chunk 只发裸文本、其他事件发 JSON（但会破坏 union 契约，不推荐）。
- **[LLM 偶发不输出 `<<CHART:>>` 标记]** → `extractChartType` 已有默认值 `"bar"`，行为不变。
- **[intent 事件暴露内部语义]** → subject/metrics/dimensions 是业务术语，不含敏感数据。SQL 不暴露，避免泄露表结构细节。
- **[Chat 增加 done 事件]** → Chat 现在靠 Flux 结束隐式关闭连接。显式 `done` 事件让前端有明确终止信号，但要求前端在 `done` 后停止累加（防止竞争）。属可接受的复杂度。
