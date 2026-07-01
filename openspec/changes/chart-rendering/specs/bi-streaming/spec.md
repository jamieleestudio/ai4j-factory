## MODIFIED Requirements

### Requirement: BI 结构化结果推送
洞察生成完成后，系统 SHALL 推送包含图表类型和数据表格的结构化结果。`chartType` 字段值域 SHALL 限定为 8 种 snake_case 标准枚举值之一：`single_value`、`bar`、`pie`、`line`、`grouped_bar`、`stacked_bar`、`heatmap`、`line_multi`。LLM 在 prompt 中被告知每种 chartType 的适用数据形状，SHALL 仅推荐与当前 `intent.dimensions` / `intent.metrics` 形状匹配的类型。

#### Scenario: 推送图表类型和数据
- **WHEN** LLM 完成洞察生成
- **THEN** 前端收到 `[result]` 事件，包含 JSON 格式的 `chartType`（取值为 8 种标准枚举之一）和 `data` 字段，前端按 `chartType` 渲染对应 ECharts 图表

#### Scenario: chartType 取值限于标准枚举
- **WHEN** LLM 输出 `chartType`
- **THEN** `chartType` 必须是 `single_value`、`bar`、`pie`、`line`、`grouped_bar`、`stacked_bar`、`heatmap`、`line_multi` 之一，不应输出其他值

#### Scenario: 多维度场景不应推荐 pie
- **WHEN** `intent.dimensions` 长度 ≥ 2
- **THEN** LLM 不应推荐 `chartType: "pie"`（pie 仅适用于 1 维度场景），应推荐 `grouped_bar` / `stacked_bar` / `heatmap` 之一

## ADDED Requirements

### Requirement: LLM 洞察 prompt 含图表类型适用场景说明
`InsightGenerationService` 的 LLM prompt SHALL 包含 8 种标准 chartType 的名称与适用数据形状说明，引导 LLM 根据当前数据形状推荐匹配的图表类型。

#### Scenario: prompt 含完整图表类型表
- **WHEN** `InsightGenerationService.buildInsightPrompt()` 构造 prompt
- **THEN** prompt 含图表类型表，列出全部 8 种 chartType 名称与适用场景描述（如 `grouped_bar: 2 维度并列对比`、`heatmap: 2 维度密度分布`）
