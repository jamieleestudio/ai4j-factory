## ADDED Requirements

### Requirement: BI 查询意图不明确时推送 clarification 事件
BI 查询接口 SHALL 在意图提取阶段，当 LLM 判定用户输入需要澄清时，推送 `clarification` 事件并提前结束流，不执行 SQL 查询和洞察生成。

#### Scenario: 意图不明确触发澄清
- **WHEN** `IntentExtractionService` 的 LLM 返回 `{"status":"needs_clarification", ...}`
- **THEN** 后端推送 `{"type":"status","stage":"analyzing","message":"正在分析你的问题..."}` 后推送 `clarification` 事件，随后推送 `done` 并关闭流，不推送 `intent`、`chunk`、`result`

#### Scenario: 意图明确正常执行
- **WHEN** LLM 返回 `{"status":"ready", "subject":..., "metrics":...}`
- **THEN** 后端走原有流程：`status → intent → status → status → chunk* → result → done`

### Requirement: BI 澄清触发场景分类
LLM SHALL 根据用户输入的模糊类型输出 `reason` 字段，后端据此构造 `options`，前端据此渲染不同引导内容。

#### Scenario: question_unclear 触发列出所有主题
- **WHEN** 用户输入无法理解（如 "1"、"asdf"）
- **THEN** LLM 输出 `reason: "question_unclear"`，后端从 `SemanticLayer` 取全部主题构造 `options`

#### Scenario: subject_ambiguous 触发列出含指标的主题
- **WHEN** 用户输入提到指标但未指定主题（如 "销售额"）
- **THEN** LLM 输出 `reason: "subject_ambiguous"`，后端从 `SemanticLayer` 取含该指标的所有主题构造 `options`

#### Scenario: metric_unspecified 触发列出该主题的指标
- **WHEN** 用户输入指定了主题但未指定指标（如 "订单分析"）
- **THEN** LLM 输出 `reason: "metric_unspecified"`，后端从 `SemanticLayer` 取该主题的所有指标构造 `options`

### Requirement: BI 查询请求支持可选 sessionId 参数
`POST /api/bi/query` 请求体 SHALL 支持可选 `sessionId` 字段，用于多轮澄清时关联上一轮的 pending clarification 上下文。未传 `sessionId` 时按全新单轮查询处理。

#### Scenario: 首次查询不传 sessionId
- **WHEN** 用户首次提问，请求体不含 `sessionId`
- **THEN** 后端按全新查询处理，调 `IntentExtractionService.extract()` 不带上下文

#### Scenario: 澄清后第二轮携带 sessionId
- **WHEN** 用户在 `clarification` 后点 chip 或重新打字，请求体携带 `sessionId`
- **THEN** 后端从 store 取 pending clarification，调 `IntentExtractionService.extractWithContext()` 带上下文

### Requirement: BI 多轮澄清通过 sessionId 关联
BI 查询接口 SHALL 支持多轮澄清：第一轮 LLM 返回 `needs_clarification` 时生成 `sessionId` 存入 `ClarificationStore` 并随 `clarification` 事件推送；用户在下一轮请求中携带 `sessionId`，后端从 store 取 pending clarification 上下文构造 LLM prompt。

#### Scenario: 第一轮生成 sessionId
- **WHEN** LLM 首次返回 `needs_clarification`
- **THEN** 后端生成 UUID 作为 `sessionId`，将 `{originalQuestion, options, createdAt}` 存入 `ClarificationStore`，TTL 5 分钟

#### Scenario: 第二轮携带 sessionId 取上下文
- **WHEN** 用户点 chip 或重新打字，请求体携带 `sessionId`
- **THEN** 后端从 store 取出 pending clarification，构造上下文 prompt（含 `originalQuestion`、`options`、用户本次输入）调 `IntentExtractionService.extractWithContext()`

#### Scenario: sessionId 过期或不存在
- **WHEN** 请求携带的 `sessionId` 在 store 中不存在（已过期或服务重启）
- **THEN** 后端忽略 `sessionId`，按全新查询处理（不带上下文调 LLM）

### Requirement: BI 澄清会话存储为 in-memory 且 TTL 5 分钟
`ClarificationStore` SHALL 为 in-memory 实现，`sessionId → pending clarification` 条目 TTL 5 分钟自动清理，容量上限 N 条防内存膨胀。

#### Scenario: TTL 过期清理
- **WHEN** store 中某条目创建后 5 分钟未被消费
- **THEN** 条目自动从 store 移除，后续携带该 `sessionId` 的请求按全新查询处理

#### Scenario: 服务重启清空
- **WHEN** 服务重启
- **THEN** store 为空，所有 pending clarification 丢失，前端若点 chip 收到 sessionId not found，回退为全新查询

### Requirement: 删除空 metrics 兜底逻辑
`IntentExtractionService` SHALL 移除"空 metrics 时自动填充第一个指标"的兜底逻辑。指标未指定时 SHALL 通过 LLM 输出 `needs_clarification`（reason: `metric_unspecified`）触发澄清，而非系统自动选择指标。

#### Scenario: 指标未指定触发澄清
- **WHEN** 用户输入指定了主题但未指定指标（如 "订单分析"）
- **THEN** LLM 输出 `{"status":"needs_clarification","reason":"metric_unspecified", ...}`，系统不自动填充第一个指标

#### Scenario: LLM 误返回 ready 但 metrics 为空
- **WHEN** LLM 返回 `{"status":"ready", "subject":..., "metrics":[]}` 且该主题有可用指标
- **THEN** 系统将此视为 `metric_unspecified` 澄清请求，推送 `clarification` 事件（不偷塞指标，不执行 SQL）
