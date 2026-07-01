## Why

BI 查询在用户输入语义模糊时（如 "1"、"销售额"、"订单分析"），IntentExtractionService 被迫"编"一个答案（如输出 `Subject: 订单分析, Metrics: 销售额`），而不是引导用户澄清。根因是协议里没有"我需要澄清"这个状态：LLM Prompt 强制输出固定 JSON 格式（无澄清状态字段），`validate()` 仅校验名字是否存在于语义层而不校验是否真的对应了用户意图，且空 metrics 时兜底逻辑会偷塞第一个指标（`IntentExtractionService.java:146-148`）。需要在意图提取阶段引入澄清路径，让系统能识别模糊输入并主动列出可用主题/指标引导用户。

## What Changes

- **LLM 输出协议支持双状态**：`IntentExtractionService` 的 Prompt 让 LLM 二选一输出 `{"status":"ready", ...}`（直接执行）或 `{"status":"needs_clarification", "reason", "message", "options"}`（请求澄清），后者触发澄清流程
- **新增 SSE `clarification` 事件类型** **BREAKING**：在统一 SSE envelope 中增加 `clarification` 事件，含 `sessionId`、`message`、`options` 字段；前端解析新增该事件分支
- **BI 查询支持澄清分支**：LLM 返回 `needs_clarification` 时，`BiController` 生成 `sessionId` 存入 store，推送 `clarification` 事件并提前结束流（不执行 SQL）；推送 `status → clarification → done`
- **多轮澄清会话状态**：新增 `ClarificationStore`（in-memory，`sessionId → pending clarification`），TTL 5 分钟；`BiController` 接收可选 `sessionId` 参数，从 store 取上下文调 `IntentExtractionService.extractWithContext()`
- **删除空 metrics 兜底逻辑**：移除 `IntentExtractionService.validate()` 中 `metrics 为空时塞第一个指标` 的逻辑（line 146-148），改为触发澄清
- **前端新增 clarification 渲染态**：`BiArea` 展示提示文案 + 可点击的 options chips；用户点 chip 把 `value` 作为 question + `sessionId` 发下一轮；用户也可重新打字（携带 `sessionId` 上下文）

## Capabilities

### New Capabilities

（无全新 capability——澄清机制是对现有 `sse-event-protocol` 和 `bi-streaming` 的扩展）

### Modified Capabilities

- `sse-event-protocol`: 新增 `clarification` 事件类型——envelope 定义、`sessionId`/`message`/`options` 字段 schema、discriminated union 契约
- `bi-streaming`: 新增 BI 查询澄清流程——意图不明确时推送 `clarification` 事件、多轮 `sessionId` 关联、提前结束流的 lifecycle、澄清触发场景（question_unclear / subject_ambiguous / metric_unspecified）

## Impact

- **后端**：
  - `IntentExtractionService.java`：Prompt 重写支持双状态输出；删除空 metrics 兜底逻辑（line 146-148）；新增 `extractWithContext(question, sessionId, context)` 方法
  - `BiController.java`：`QueryRequest` record 新增可选 `sessionId` 字段；处理 `needs_clarification` 分支；推送 `ClarificationEvent`；提前结束流
  - 新增 `ClarificationStore`（in-memory，TTL 5 分钟，容量限制防内存膨胀）
  - 新增 `ClarificationEvent` SSE DTO（record）
  - `QueryIntent` 旁新增 `ClarificationResult` 类型承载 LLM 双状态输出
- **前端**：
  - `fetchSSE.ts`：discriminated union 新增 `clarification` 分支
  - `BiArea.tsx`：新增 `clarification` 渲染态（提示文案 + chips + `sessionId` 持有）；`BiMessage` 联合类型新增 clarification 状态
  - `ChatInput`：chip 点击时携带 `sessionId` 发送下一轮
- **API 变更** **BREAKING**：`POST /api/bi/query` 请求体新增可选 `sessionId` 字段（向后兼容，未传时走单轮流程）；SSE 流新增 `clarification` 事件类型
- **依赖**：
  - 依赖 `unified-sse-protocol` change 已归档（`sse-event-protocol` 基础事件 envelope 已建立，`clarification` 作为新事件类型加入）；实施顺序上 `clarification-flow` 应在 `unified-sse-protocol` 完成后进行
  - 复用 in-memory session 基础设施（参考 commit `feat(bi): implement in-memory session history for BI queries`）
- **范围排除**：暂不做 pre-check 规则前置过滤（如长度 < 3、纯数字检测）。理由：用户要求语义模糊也走引导，必须 LLM 判断；规则维护成本高且跟语义层演进耦合。后续若需降低 LLM 成本，可考虑加轻量分类器
