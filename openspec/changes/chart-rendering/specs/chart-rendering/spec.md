## ADDED Requirements

### Requirement: 前端按数据形状推断图表候选池
前端 SHALL 在收到 `intent` 事件后，根据 `intent.dimensions`（含 `type`）、`intent.metrics` 推断候选图表类型列表（候选池）。候选池为有序数组，首项作为 LLM 推荐值无效时的 fallback。

#### Scenario: 0 维度 1 指标
- **WHEN** `intent.dimensions` 为空数组且 `intent.metrics` 长度为 1
- **THEN** 候选池为 `["single_value"]`，仅渲染 KPI 卡片

#### Scenario: 1 维度（非时间）1 指标
- **WHEN** `intent.dimensions` 长度为 1 且 `type !== "TIME"`，`intent.metrics` 长度为 1
- **THEN** 候选池为 `["bar", "pie"]`，首项 `bar` 为 fallback

#### Scenario: 1 维度（时间）1 指标
- **WHEN** `intent.dimensions` 长度为 1 且 `type === "TIME"`，`intent.metrics` 长度为 1
- **THEN** 候选池为 `["line", "bar"]`，首项 `line` 为 fallback

#### Scenario: 2 维度（非时间）1 指标
- **WHEN** `intent.dimensions` 长度为 2 且均 `type !== "TIME"`，`intent.metrics` 长度为 1
- **THEN** 候选池为 `["grouped_bar", "stacked_bar", "heatmap"]`，不含 `pie`

#### Scenario: 2 维度（含时间）1 指标
- **WHEN** `intent.dimensions` 长度为 2 且至少一项 `type === "TIME"`，`intent.metrics` 长度为 1
- **THEN** 候选池为 `["line_multi", "grouped_bar", "stacked_bar"]`，首项 `line_multi` 为 fallback

#### Scenario: 3+ 维度退化到表格
- **WHEN** `intent.dimensions` 长度 ≥ 3
- **THEN** 候选池为空数组 `[]`，前端不渲染图表区，仅显示表格 + 提示"维度过多，暂不支持自动可视化"

#### Scenario: 多指标退化到表格
- **WHEN** `intent.metrics` 长度 ≥ 2
- **THEN** 候选池为空数组 `[]`，前端不渲染图表区，仅显示表格

### Requirement: LLM 推荐值不在候选池时 fallback
前端 SHALL 校验 LLM 推荐的 `chartType`（来自 `result` 事件）是否属于当前数据形状的候选池。若不在候选池内，前端 SHALL fallback 到候选池首项并记录 warning 日志。

#### Scenario: LLM 推荐值有效
- **WHEN** LLM 推荐 `chartType: "grouped_bar"`，候选池为 `["grouped_bar", "stacked_bar", "heatmap"]`
- **THEN** 前端使用 `grouped_bar` 作为 `activeChart` 渲染图表

#### Scenario: LLM 推荐值无效
- **WHEN** LLM 推荐 `chartType: "pie"`，但数据形状为 2 维度（候选池 `["grouped_bar", "stacked_bar", "heatmap"]`）
- **THEN** 前端 fallback 到 `grouped_bar` 作为 `activeChart`，控制台输出 warning 日志记录原始推荐值与 fallback 决策

#### Scenario: 候选池为空
- **WHEN** 候选池为空数组（3+ 维度或多指标场景）
- **THEN** 前端忽略 LLM 推荐值，不渲染图表区，仅渲染表格

### Requirement: 前端图表切换 UI
前端 SHALL 在图表区下方渲染 chips 行，每个 chip 对应候选池中的一种图表类型。当前 `activeChart` 对应的 chip 用实心填充样式，其他用 outline 样式。

#### Scenario: 渲染候选 chips
- **WHEN** 候选池为 `["grouped_bar", "stacked_bar", "heatmap"]`，`activeChart` 为 `grouped_bar`
- **THEN** 渲染 3 个 chips：`分组柱状图`（实心）、`堆叠柱状图`（outline）、`热力图`（outline）

#### Scenario: 用户切换图表
- **WHEN** 用户点击 `stacked_bar` chip
- **THEN** `activeChart` 更新为 `stacked_bar`，图表区重新渲染为堆叠柱状图，chips 样式同步更新（`stacked_bar` 变实心，`grouped_bar` 变 outline）

#### Scenario: 切换不触发网络请求
- **WHEN** 用户点击 chip 切换图表
- **THEN** 切换为纯前端 state 更新，不调用 `fetchSSE`，不重新请求 `/api/bi/query`

#### Scenario: 切换状态不持久化
- **WHEN** 用户切换到 `heatmap` 后刷新页面或重新提问
- **THEN** `activeChart` 重置为 LLM 推荐值（或 fallback 后的候选池首项），不保留用户上次的切换选择

### Requirement: 根据 chartType 构造 ECharts option
前端 SHALL 通过纯函数 `buildOption(chartType, data, intent)` 根据 `chartType` + `data` + `intent` 构造 ECharts option 对象。每种 chartType 对应确定的字段映射规则。

#### Scenario: bar 字段映射
- **WHEN** `chartType` 为 `bar`，`intent.dimensions[0].name` 为 "区域"，`intent.metrics[0]` 为 "销售额"
- **THEN** ECharts option 的 `xAxis.data` 为 data 中所有 "区域" 值，`series[0].type` 为 `"bar"`，`series[0].data` 为 data 中所有 "销售额" 值

#### Scenario: pie 字段映射
- **WHEN** `chartType` 为 `pie`
- **THEN** ECharts option 的 `series[0].type` 为 `"pie"`，`series[0].data` 为 `data.map(row => ({name: row[dim[0]], value: row[metric[0]]}))`

#### Scenario: grouped_bar 字段映射
- **WHEN** `chartType` 为 `grouped_bar`，2 维度 + 1 指标
- **THEN** ECharts option 的 `xAxis.data` 为 `dim[0]` 唯一值数组，`series` 为 `dim[1]` 唯一值数组映射出的多个 `{type:"bar", name:<dim[1]值>, data:[...]}` 对象

#### Scenario: stacked_bar 字段映射
- **WHEN** `chartType` 为 `stacked_bar`
- **THEN** 与 `grouped_bar` 字段映射一致，但每个 series 额外含 `stack: "total"` 字段实现堆叠

#### Scenario: heatmap 字段映射
- **WHEN** `chartType` 为 `heatmap`
- **THEN** ECharts option 的 `xAxis.data` 为 `dim[0]` 唯一值，`yAxis.data` 为 `dim[1]` 唯一值，`series[0].type` 为 `"heatmap"`，`series[0].data` 为 `data.map(row => [x_idx, y_idx, metric[0]])`，`visualMap` 配置 min/max 与渐变色

#### Scenario: line_multi 字段映射
- **WHEN** `chartType` 为 `line_multi`，2 维度（含时间）+ 1 指标
- **THEN** ECharts option 的 `xAxis.data` 为 `dim[0]`（TIME 维度）唯一值，`series` 为 `dim[1]` 唯一值数组映射出的多个 `{type:"line", name:<dim[1]值>, data:[...]}` 对象

#### Scenario: single_value 不构造 ECharts option
- **WHEN** `chartType` 为 `single_value`
- **THEN** `buildOption` 返回 `null`，`ChartRenderer` 渲染 KPI 卡片（指标名作标题，数值格式化显示），不渲染 ECharts

### Requirement: EChart 组件生命周期管理
`EChart.tsx` SHALL 在 mount 时初始化 ECharts 实例，在 `option` 变化时调用 `setOption`，在窗口 resize 时调用 `resize`，在 unmount 时调用 `dispose` 释放资源。

#### Scenario: mount 初始化
- **WHEN** `EChart` 组件 mount，`ref` 指向的 DOM 节点存在
- **THEN** 调用 `echarts.init(domNode)` 初始化实例，并立即 `setOption(option)` 渲染初始图表

#### Scenario: option 更新
- **WHEN** `option` prop 变化（如用户切换 chartType）
- **THEN** 调用 `chartRef.setOption(option, true)` 更新图表（`true` 表示不合并 option，完整替换）

#### Scenario: 窗口 resize 自适应
- **WHEN** 窗口或父容器尺寸变化（如侧栏开合导致主区域宽度变化）
- **THEN** 调用 `chartRef.resize()` 让 ECharts 重新计算尺寸

#### Scenario: unmount 释放
- **WHEN** `EChart` 组件 unmount
- **THEN** 调用 `chartRef.dispose()` 释放 ECharts 实例，避免内存泄漏

#### Scenario: SSR 安全
- **WHEN** Next.js 进行服务端渲染
- **THEN** `EChart.tsx` 标注 `'use client'` 指令，所有 `echarts.init` / DOM 访问在 `useEffect` 内执行，不在模块顶层或 render 阶段访问 `window` / `document`

### Requirement: BI 渲染态视觉容器最小化
前端 SHALL 仅在 Thinking Block、Chart Area、Data Table 三处使用视觉容器（边框 + 背景）。Insight 文本（streaming 阶段逐字追加 + success 阶段最终展示）SHALL 以纯文字形式渲染，无边框、无 "Insight" 小标签、无闪烁光标。

#### Scenario: streaming 阶段 Insight 纯文字追加
- **WHEN** 后端通过 `chunk` 事件流式输出 Insight 文本
- **THEN** 前端逐字追加文字到当前 assistant 消息的 `streamingText` 字段，渲染为纯文字段落，无边框容器、无 "Insight" 小标签、无闪烁光标

#### Scenario: success 阶段 Insight 纯文字展示
- **WHEN** 后端 `done` 事件结束流式输出，前端将 `streamingText` 提升为 `result.summary`
- **THEN** 前端将 `result.summary` 渲染为纯文字段落，无边框容器、无 "Insight" 小标签

### Requirement: Data Table 高度限制与显示规则
前端 SHALL 对 Data Table 容器应用 `max-h-80`（约 10 行预览高度）+ `overflow-y-auto`，超出部分通过内部滚动查看。`single_value` 场景（0 维度 1 指标）SHALL 不渲染 Data Table。

#### Scenario: Data Table 高度限制
- **WHEN** `result.data` 行数超过 10 行
- **THEN** Data Table 容器渲染为 `max-h-80 overflow-y-auto`，前约 10 行可见，后续行通过容器内部滚动查看

#### Scenario: single_value 场景不渲染表格
- **WHEN** `activeChart` 为 `single_value`（候选池为 `["single_value"]`，即 0 维度 1 指标场景）
- **THEN** 前端仅渲染 KPI 卡片，不渲染 Data Table（KPI 卡已完整表达数据，表格冗余）

#### Scenario: 其他 chartType 渲染表格
- **WHEN** `activeChart` 为 `bar` / `pie` / `line` / `grouped_bar` / `stacked_bar` / `heatmap` / `line_multi` 之一，且 `result.data` 非空
- **THEN** 前端渲染 Chart Area + Data Table（限高 + 滚动）

#### Scenario: 候选池为空时渲染表格
- **WHEN** 候选池为空数组（3+ 维度或 2+ 指标场景），且 `result.data` 非空
- **THEN** 前端不渲染 Chart Area，仅渲染 Data Table（限高 + 滚动）+ "维度过多，暂不支持自动可视化" 提示
