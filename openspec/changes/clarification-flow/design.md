## Context

BI 查询流程当前是单轮、无状态的：用户输入问题 → `IntentExtractionService` 调 LLM 提取 `QueryIntent` → `SqlBuilder` 生成 SQL → `QueryExecutionService` 执行 → `InsightGenerationService` 流式生成洞察。整个链路假设 LLM 总能从用户输入中提取出明确的查询意图。

但实际中用户输入常常是模糊的：
- 垃圾输入："1"、"asdf"
- 主题模糊："销售额"（哪个主题下的销售额？）
- 指标未指定："订单分析"（看订单的什么指标？）

当前代码三处缺陷导致系统"编"答案而非引导：
1. `IntentExtractionService.buildExtractionPrompt()` 强制 LLM 输出固定 JSON 格式 `{"subject":..., "metrics":..., ...}`，没有"我需要澄清"这个状态字段
2. `validate()` 仅校验名字是否存在于语义层，不校验是否真的对应了用户意图
3. `validate()` line 146-148：`if (intent.getMetrics().isEmpty()) intent.getMetrics().add(subject.getMetrics().get(0).getName())` — 空 metrics 时偷塞第一个指标

`unified-sse-protocol` change（in-progress, 34/38）正在建立统一 SSE 事件 envelope（`status`/`intent`/`chunk`/`result`/`error`/`done`），`clarification` 作为新事件类型加入正合时机。

## Goals / Non-Goals

**Goals:**
- 让 BI 查询能识别模糊输入并主动引导用户（列出可用主题/指标）
- 支持多轮澄清：用户点 chip 或重新打字，系统能关联上下文
- 复用 `unified-sse-protocol` 的 JSON envelope 设计，`clarification` 作为新事件类型
- 删除"空 metrics 偷塞第一个指标"的兜底逻辑

**Non-Goals:**
- 不做 pre-check 规则前置过滤（长度/纯数字检测）——语义模糊必须 LLM 判断
- 不做持久化澄清会话——in-memory 即可，TTL 5 分钟
- 不做澄清会话的并发控制——单用户单澄清场景足够
- 不重构 `IntentExtractionService` 的整体架构——仅扩展 Prompt 和新增方法

## Decisions

### Decision 1: LLM 单次调用双状态输出

LLM Prompt 让 LLM 二选一输出：
- `{"status":"ready", "subject":..., "metrics":..., "dimensions":..., "filters":...}` — 直接执行
- `{"status":"needs_clarification", "reason":"question_unclear"|"subject_ambiguous"|"metric_unspecified", "message":..., "options":[...]}` — 请求澄清

**Alternatives considered:**
- 两次 LLM 调用（先判断是否模糊，再提取意图）：成本翻倍，延迟翻倍
- 规则前置过滤：挡不住"销售额"这种语义模糊，且规则要随语义层演进维护

**Why:** 单次调用 + 双状态输出让 LLM 在提取意图的同时判断是否需要澄清，成本不变、覆盖最广。

### Decision 2: clarification 作为新 SSE 事件类型

在 `sse-event-protocol` 的事件类型集合中新增 `clarification`，载荷：
```json
{"type":"clarification","sessionId":"<uuid>","message":"<text>","options":[{"label":"<label>","value":"<value>","description":"<text>"}]}
```

**Alternatives considered:**
- 用 `error` 事件承载澄清：语义错误，澄清不是错误，前端渲染态不同（澄清是引导而非异常）
- 用 `status` 事件 + 前端解析 message 字符串：违背 `unified-sse-protocol` 的 JSON envelope 原则，前端要字符串匹配

**Why:** 澄清是独立的状态机分支（提前结束流、不执行 SQL），需要独立事件类型和独立前端渲染态。

### Decision 3: 多轮会话用 sessionId 关联 + InMemoryClarificationStore

`BiController.QueryRequest` 新增可选 `sessionId` 字段。LLM 返回 `needs_clarification` 时生成 `sessionId` 存入 store；用户点 chip 或重新打字时携带 `sessionId`，`BiController` 从 store 取上下文调 `extractWithContext()`。

Store 结构：`sessionId (UUID) → {originalQuestion, options, selectedValue, createdAt}`，TTL 5 分钟，容量上限 N 条（防内存膨胀）。

**Alternatives considered:**
- 把上下文塞进 question 字段：用户输入被污染，前端要拼字符串
- 用 HTTP session：BI 当前无状态，引入 session 复杂度高
- 用 conversationId 关联整个对话：超出当前需求，澄清只需关联上一轮

**Why:** sessionId 关联单次澄清上下文，轻量、无状态侵入，TTL 自动清理。复用已有 in-memory session 基础设施（commit `feat(bi): implement in-memory session history for BI queries`）。

### Decision 4: 删除空 metrics 兜底逻辑

移除 `IntentExtractionService.validate()` line 146-148 的 `if (intent.getMetrics().isEmpty()) intent.getMetrics().add(subject.getMetrics().get(0).getName())`。

**Alternatives considered:**
- 保留兜底但加日志：治标不治本，用户还是看到"编"的答案
- 兜底改为触发澄清：需要 `validate()` 能返回澄清信号，破坏当前 validate-or-throw 契约

**Why:** 空 metrics 是 LLM 判定"指标未指定"的合法信号，应在 Prompt 阶段就让 LLM 输出 `needs_clarification`，而非在 validate 阶段偷塞。删除兜底让"空 metrics = 需要澄清"的语义清晰。若 LLM 误返回 `ready` + 空 metrics，系统兜底转为 `metric_unspecified` 澄清（不偷塞指标，不执行 SQL）。

### Decision 5: reason 枚举三种触发场景

- `question_unclear`：输入无法理解（"1"、"asdf"）→ 列出所有主题
- `subject_ambiguous`：指标跨多个主题（"销售额"）→ 列出含该指标的主题
- `metric_unspecified`：主题明确但指标未指定（"订单分析"）→ 列出该主题的所有指标

**Alternatives considered:**
- LLM 自由生成 reason 字符串：前端无法按类型渲染，国际化困难
- 不分 reason，统一列出所有主题：体验差，"订单分析"时用户已经指定主题

**Why:** 枚举让前端能按场景渲染不同引导内容（如列出主题 vs 列出指标），且便于后续扩展。

### Decision 6: options 完全由后端从 SemanticLayer 构造，不由 LLM 生成

`clarification` 事件的 `options` 列表完全由后端根据 `reason` + 上下文从 `SemanticLayer` 构造，LLM 不输出 options。LLM 只输出 `reason` + `message` + 可选上下文（`subject` 用于 `metric_unspecified`、`metric` 用于 `subject_ambiguous`）。后端构造逻辑：
- `question_unclear`：从 `SemanticLayer.getAllSubjects()` 取全部主题
- `subject_ambiguous`（含 `metric`）：从 `SemanticLayer` 取含该指标的所有主题
- `metric_unspecified`（含 `subject`）：从该主题取所有指标

每个 option 的 `label` 和 `value` 均为主题/指标名，`description` 从 `Subject.getDescription()` / `Metric.getDescription()` 取。

**Alternatives considered:**
- LLM 输出 options（label+value），后端补 description：LLM 可能幻觉出不存在的主题/指标名，后端查不到 description 时要降级处理；且 LLM 输出量增大
- LLM 全量生成 options（含 description）：增加 LLM 输出量，且可能与语义层不一致

**Why:** 后端从语义层构造 options 保证名字一定存在、description 一定有值；LLM 输出量最小（只输出 reason+message+context）；消除了 LLM 幻觉 option 名字的风险。与 `bi-streaming/spec.md` 的 scenario 一致（"后端从 SemanticLayer 取...构造 options"）。

## Risks / Trade-offs

- **[依赖 unified-sse-protocol 归档]** `clarification-flow` 的 `sse-event-protocol` spec delta 使用 ADDED Requirements，依赖 `unified-sse-protocol` 先归档建立基础 envelope spec → 实施顺序上 `clarification-flow` 必须在 `unified-sse-protocol` 完成后进行；proposal 的 Impact 部分已标注此依赖
- **[LLM 澄清判断不稳定]** LLM 可能在该澄清时输出 `ready`（编答案），或在已明确时输出 `needs_clarification`（多余引导） → Prompt 中给出清晰的澄清触发示例；保留 retry 机制（现有 `MAX_RETRIES=2`）在 `ready` 状态校验失败时重试
- **[in-memory store 重启丢失]** 服务重启时 pending clarification 全部丢失，用户点 chip 会找不到 session → store 为 in-memory 设计，重启属可接受边界；前端 chip 点击若返回 sessionId not found 错误，回退为全新查询（不带 sessionId）
- **[TTL 边界]** 用户思考超过 5 分钟后点 chip，session 已过期 → 前端收到 sessionId not found 时回退为全新查询（不带 sessionId），重新走 LLM 提取
- **[BREAKING: SSE 新增事件类型]** 前端旧版本不解析 `clarification` 事件，会静默跳过（`unified-sse-protocol` 的"未知 type 降级"规则），用户看不到引导 → 内部项目，前后端同步发布，无版本协商

## Migration Plan

1. 先完成 `unified-sse-protocol`（剩余 4 个任务），归档后 `sse-event-protocol` 基础 spec 建立
2. 实施 `clarification-flow`：后端先（`IntentExtractionService` → `ClarificationStore` → `BiController` → `ClarificationEvent` DTO），前端后（`fetchSSE` → `BiArea` → `ChatInput`）
3. 端到端测试：输入 "1" 验证触发 `clarification`，点 chip 验证多轮关联
4. 无数据迁移（in-memory store，无持久化）

## Open Questions

- 多轮澄清的最大轮数限制？倾向不限制（用户可反复澄清），store TTL 5 分钟自然限制。若需限制可在 `BiController` 加 counter，留待实施时决定。
- 澄清 `options` 的最大数量？语义层主题/指标数量有限，暂不设上限。若语义层膨胀可在 Prompt 中加"最多列出 8 个"约束。
