## ADDED Requirements

### Requirement: BI 管线 trace 事件推送
BI 查询 SSE 流 SHALL 在管线每个阶段推送 `type:"trace"` 事件，作为对现有 `status`/`intent`/`chunk`/`result`/`done` 事件的补充。trace 事件 SHALL 与现有事件并存，前端忽略 `trace` 类型时仍能完成基础渲染。

每个 trace 事件 SHALL 包含 `spanId`（请求内唯一标识）、`name`（阶段名）、`status`（`"START"` 或 `"END"`）字段，可选包含 `parentId`（父 span ID）和 `attributes`（载荷对象）。

长步骤（`intent-extraction`、`llm-call`、`query-execute`、`insight-generation`）SHALL 推送 `START` 和 `END` 两个事件；短步骤（`semantic-context`、`sql-build`）SHALL 仅推送 `END` 事件。

#### Scenario: trace 事件基础形状
- **WHEN** BI 管线任意阶段开始或结束
- **THEN** SSE 流推送 `data: {"type":"trace","spanId":"...","name":"...","status":"START|END","attributes":{...}}` 格式事件

#### Scenario: 长步骤双事件
- **WHEN** `intent-extraction` 阶段开始
- **THEN** 推送 `{"type":"trace","spanId":"s2","name":"intent-extraction","status":"START"}`，结束后推送对应 `status:"END"` 事件

#### Scenario: 短步骤单事件
- **WHEN** `semantic-context` 阶段（语义层已加载到内存）完成
- **THEN** 仅推送 `{"type":"trace","spanId":"s1","name":"semantic-context","status":"END","attributes":{...}}`，无 `START` 事件

#### Scenario: trace 事件向后兼容
- **WHEN** 前端未实现 `trace` 事件处理逻辑
- **THEN** 前端按 `type` 字段分发时对 `"trace"` 走默认忽略分支，`status`/`intent`/`chunk`/`result`/`done` 事件处理不受影响

### Requirement: 语义层 trace 事件载荷
`semantic-context` trace 事件的 `attributes.subjects` SHALL 携带可用主题、指标、维度的业务信息，让用户看到 LLM 在什么菜单里选择。

`attributes.subjects` SHALL 为数组，每项含 `name`、`description`、`metrics`（数组，每项含 `name`、`description`、`aggregation`）、`dimensions`（数组，每项含 `name`、`type`）。

`semantic-context` trace 事件 SHALL NOT 暴露 `column`、`table` 或任何物理 schema 字段。

#### Scenario: 语义层 trace 暴露业务字段
- **WHEN** BI 管线加载语义层完成，推送 `semantic-context` trace 事件
- **THEN** `attributes.subjects` 数组中每个主题含 `name`、`description`、`metrics`（含 `name`/`description`/`aggregation`）、`dimensions`（含 `name`/`type`），如 `{"name":"销售额","description":"订单金额总和","aggregation":"SUM"}`

#### Scenario: 语义层不暴露物理字段
- **WHEN** `semantic-context` trace 事件被序列化
- **THEN** 事件载荷中不含 `column`、`table` 字段，主题对象只有业务名和描述

### Requirement: 意图提取重试 trace 可见性
`intent-extraction` span 下的每次 LLM 调用 SHALL 作为独立的 `llm-call` 子 span（`parentId` 指向 `intent-extraction` spanId），无论成功或失败都推送 `END` 事件。

每个 `llm-call` 子 span 的 `attributes` SHALL 包含 `attempt` 字段（整数，从 1 开始）和 `rawOutput` 字段（LLM 原始输出字符串）。

失败的 `llm-call` 子 span 的 `attributes` SHALL 额外包含 `error` 字段，描述解析失败或校验失败的具体原因。

重试的 `llm-call` 子 span（`attempt > 1`）的 `START` 事件 SHALL 在 `attributes` 中包含 `feedback` 字段，携带给 LLM 的修正反馈文案。

#### Scenario: 成功的单次意图提取
- **WHEN** 用户问题清晰，第一次 LLM 调用即成功解析并通过校验
- **THEN** SSE 流推送：`intent-extraction START` → `llm-call(attempt:1) START` → `llm-call(attempt:1, rawOutput:"{...}") END` → `intent-extraction END`

#### Scenario: 失败后重试成功
- **WHEN** 第一次 LLM 调用返回的 JSON 引用了不存在的 metric，校验失败后系统带 feedback 重试
- **THEN** SSE 流推送：`intent-extraction START` → `llm-call(attempt:1) START` → `llm-call(attempt:1, rawOutput, error:"Unknown metric 'sales'") END` → `llm-call(attempt:2, feedback:"输出未通过解析或校验...") START` → `llm-call(attempt:2, rawOutput) END` → `intent-extraction END`

#### Scenario: 重试次数耗尽
- **WHEN** LLM 调用连续失败超过 `MAX_RETRIES+1` 次
- **THEN** 已推送的失败 `llm-call` 子 span 仍可见，随后推送 `error` 事件和 `done` 事件

### Requirement: 洞察生成阶段 trace 不含 rawOutput
`insight-generation` span 的 `END` 事件 SHALL NOT 在 `attributes` 中包含 `rawOutput` 字段，因为洞察文本已通过 `chunk` 事件流式推送。

`insight-generation` span 的 `END` 事件 SHALL 在 `attributes` 中包含 `chartType` 字段（从 `extractChartType` 提取的图表类型）。

#### Scenario: 洞察生成 trace 仅含元信息
- **WHEN** 洞察生成流式完成，`insight-generation` span 推送 `END` 事件
- **THEN** 事件 `attributes` 含 `chartType`（如 `"bar"`），不含 `rawOutput`

### Requirement: 前端 trace 时间线渲染
前端 `ThinkingBlock` SHALL 将 trace 事件按 `parentId` 组织为 span 树，内联渲染在助手消息气泡顶部。

流式过程中（消息状态为 `loading` 或 `streaming`）SHALL 默认展开时间线，实时点亮每个 span 的状态（已结束为实心圆点，进行中为脉冲圆点）。

`done` 事件到达后 SHALL 自动折叠时间线为单行摘要（如「思考过程 · 3.2s · 4 步」），点击可重新展开。

每个 span SHALL 支持点击展开查看 `attributes` 详情（包括 `rawOutput`、`error`、`feedback` 等字段）。

#### Scenario: 流式过程中时间线展开
- **WHEN** BI 查询进行中，trace 事件持续推送
- **THEN** `ThinkingBlock` 默认展开，按 span 到达顺序渲染时间线，已 `END` 的 span 显示为完成态，仅有 `START` 的 span 显示为进行中态

#### Scenario: 完成后自动折叠
- **WHEN** `done` 事件到达，BI 查询结束
- **THEN** `ThinkingBlock` 自动折叠为单行摘要，显示总耗时和 span 数量，点击可重新展开

#### Scenario: 嵌套 span 缩进显示
- **WHEN** trace 事件含 `parentId`（如 `llm-call` 的 parentId 指向 `intent-extraction`）
- **THEN** 前端将该 span 缩进渲染在父 span 下方，让用户看出重试是 `intent-extraction` 的子步骤

#### Scenario: span attributes 可展开查看
- **WHEN** 用户点击时间线中某个 span
- **THEN** 该 span 展开显示 `attributes` 对象的所有字段（包括 `rawOutput`、`error`、`feedback`、`attempt` 等），让用户看到 LLM 原始输出和失败原因
