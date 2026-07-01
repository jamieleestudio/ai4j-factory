## 1. 前置确认

- [x] 1.1 确认 `unified-sse-protocol` change 已归档（`openspec/specs/sse-event-protocol/spec.md` 存在，基础事件 envelope spec 已建立，含 `intent` 事件结构定义）— 未归档（34/38），但代码层已就绪：`BiController` 已用 `SseEventSerializer` + `StatusEvent`/`IntentEvent`/`ChunkEvent`/`ResultEvent`/`ClarificationEvent`/`DoneEvent`/`ErrorEvent`，spec delta 在 `openspec/changes/unified-sse-protocol/specs/sse-event-protocol/spec.md`
- [x] 1.2 确认 `clarification-flow` change 已归档（`sse-event-protocol` spec 含 `clarification` 事件类型，`bi-streaming` spec 含澄清流程 requirement）— 未归档（36/39），但代码层已就绪：`ClarificationStore`、`ClarificationEvent`、`IntentExtractionResult.NeedsClarification`、`BiController.handleClarification()` 均已实现
- [x] 1.3 确认 `BiController.toIntentEvent()` 当前输出 `dimensions` 为 `List<String>`（仅名字，无 type）— 已确认，`BiController.java:222` 直接传 `intent.getDimensions()`（`List<String>`）
- [x] 1.4 确认 `InsightGenerationService.buildInsightPrompt()` 当前图类型表仅 4 种（`single_value/bar/line/pie`）— 已确认，`InsightGenerationService.java:116-120`

## 2. 后端：IntentEvent dimensions 扩展 type 字段

- [x] 2.1 新增 `DimensionRef` record（`org.ai4j.factory.sse` 包）：`{name: String, type: String}`，`type` 取值为 `"STRING"` / `"TIME"` 等语义层 dimension type
- [x] 2.2 修改 `IntentEvent` record：`dimensions` 字段类型从 `List<String>` 改为 `List<DimensionRef>`
- [x] 2.3 修改 `BiController.toIntentEvent()`：从 `Subject.getDimensions()` 取 `Dimension.getType()`，构造 `DimensionRef` 列表填入 `IntentEvent`
- [x] 2.4 确认 `SseEventSerializer` 序列化 `List<DimensionRef>` 输出 `[{name, type}, ...]` 结构（record 默认序列化即可）— Jackson 默认 record 序列化即输出 `[{name, type}, ...]`，`SseEventSerializer` 用 `ObjectMapper.writeValueAsString`，无需改动

## 3. 后端：LLM prompt 图类型表更新

- [x] 3.1 修改 `InsightGenerationService.buildInsightPrompt()` 的图类型表，从 4 种扩展为 8 种标准值：`single_value`、`bar`、`pie`、`line`、`grouped_bar`、`stacked_bar`、`heatmap`、`line_multi`
- [x] 3.2 每种 chartType 附适用场景说明（如 `grouped_bar: 2 维度并列对比`、`heatmap: 2 维度密度分布`、`line_multi: 1 时间维度 + 1 分组维度的多序列趋势`）
- [x] 3.3 prompt 中明确约束：多维度（≥2 维度）场景不应推荐 `pie`，应推荐 `grouped_bar`/`stacked_bar`/`heatmap`

## 4. 前端：依赖与类型基础

- [x] 4.1 `apps/ai4j-factory-ui/package.json` 新增依赖 `echarts: ^5.5.0`（实际安装 5.6.0）
- [x] 4.2 新增 `src/lib/chartTypes.ts`：导出 `ChartType` union 类型（8 种字面量）、`chartLabel(type: ChartType): string` 函数（返回中文显示名，如 `grouped_bar` → "分组柱状图"）
- [x] 4.3 修改 `src/utils/fetchSSE.ts`：`IntentPayload.dimensions` 类型从 `string[]` 改为 `{ name: string; type: string }[]`
- [x] 4.4 修改 `BiArea.tsx` 的 `ThinkingBlock`：渲染 dimensions 时从 `dim`（字符串）改为 `dim.name`

## 5. 前端：候选池推断

- [x] 5.1 新增 `src/lib/chartPool.ts`：导出 `inferChartPool(intent: { dimensions: {name,type}[]; metrics: string[] }): ChartType[]`，按 design.md 规则表实现（0/1/2 维度 × isTime × nmetric）
- [x] 5.2 `isTime` 判定：`intent.dimensions.some(d => d.type === "TIME")`
- [x] 5.3 边界场景：3+ 维度或 2+ 指标返回空数组 `[]`
- [x] 5.4 新增 `src/lib/chartPool.test.ts`：覆盖规则表所有分支（0 dim、1 dim 非时间、1 dim 时间、2 dim 非时间、2 dim 含时间、3+ dim、2+ metric）— 9 个测试用例全部通过

## 6. 前端：ECharts option 构造

- [x] 6.1 新增 `src/lib/chartOption.ts`：导出 `buildOption(chartType: ChartType, data: Record<string, unknown>[], intent: IntentPayload): EChartsOption | null`
- [x] 6.2 实现 `single_value` 分支：返回 `null`（由 ChartRenderer 渲染 KPI 卡片）
- [x] 6.3 实现 `bar` 分支：`xAxis.data = data.map(r => r[dim[0].name])`，`series[0] = {type:'bar', data: data.map(r => r[metric[0]])}`
- [x] 6.4 实现 `pie` 分支：`series[0] = {type:'pie', data: data.map(r => ({name: r[dim[0].name], value: r[metric[0]]}))}`
- [x] 6.5 实现 `line` 分支：与 bar 类似但 `series[0].type = 'line'`
- [x] 6.6 实现 `grouped_bar` 分支：`xAxis.data = dim[0] unique`，`series = dim[1] unique values.map(v => ({name:v, type:'bar', data: dim[0].map(xv => findRow(xv, v)?.[metric[0]] ?? 0)}))`
- [x] 6.7 实现 `stacked_bar` 分支：与 grouped_bar 一致但每个 series 加 `stack: 'total'`
- [x] 6.8 实现 `heatmap` 分支：`xAxis/yAxis` 为 dim[0]/dim[1] unique，`series[0] = {type:'heatmap', data: data.map(r => [x_idx, y_idx, r[metric[0]]])}`，`visualMap` 配置 min/max
- [x] 6.9 实现 `line_multi` 分支：与 grouped_bar 类似但 `series[*].type = 'line'`，`xAxis` 为 TIME 维度（用 `dims.find(d => d.type === 'TIME')` 定位时间维度，找不到时 fallback 到 dim[0]）
- [x] 6.10 新增 `src/lib/chartOption.test.ts`：每个 chartType × fixture 数据 → 断言 option JSON 结构（13 个测试用例全部通过）

## 7. 前端：EChart wrapper 组件

- [x] 7.1 新增 `src/components/EChart.tsx`，文件首行 `'use client'` 指令
- [x] 7.2 mount 时 `echarts.init(ref.current)`，立即 `setOption(option, true)`
- [x] 7.3 `option` prop 变化时 `setOption(option, true)`
- [x] 7.4 监听 `window.resize` 调用 `chartRef.resize()`，unmount 时移除监听
- [x] 7.5 unmount 时 `chartRef.dispose()` 释放资源
- [x] 7.6 按需引入 ECharts：`echarts/core` + `BarChart/LineChart/PieChart/HeatmapChart` + `GridComponent/TooltipComponent/LegendComponent/VisualMapComponent/TitleComponent` + `CanvasRenderer`，调用 `echarts.use([...])`

## 8. 前端：ChartRenderer 与 ChartSwitcher

- [x] 8.1 新增 `src/components/ChartRenderer.tsx`：props `{ chartType, data, intent }`，`single_value` 渲染 KPI 卡片（指标名作标题、数值格式化千分位），其他类型调 `buildOption` + `<EChart option={option} />`
- [x] 8.2 KPI 卡片样式：标题用 `text-xs text-gray-500`，数值用 `text-2xl font-medium`
- [x] 8.3 新增 `src/components/ChartSwitcher.tsx`：props `{ candidateCharts: ChartType[], activeChart: ChartType, onChange: (type: ChartType) => void }`
- [x] 8.4 chips 渲染：`candidateCharts.map(c => <button>{chartLabel(c)}</button>)`，`activeChart` 对应 chip 实心填充、其他 outline
- [x] 8.5 chip 样式参考 `ClarificationBlock` 的 button 风格（圆角、hover 边框）

## 9. 前端：BiArea 集成

- [x] 9.1 `BiMessage` 联合类型 success 状态新增 `activeChart?: ChartType` 字段
- [x] 9.2 `onResult` 回调中初始化 `activeChart`：校验 `result.chartType` ∈ `inferChartPool(intent)`，有效则用 LLM 推荐值，无效则 fallback 到候选池[0] + console.warn
- [x] 9.3 success 渲染态在 Insight 蓝框后、Data 表格前插入图表区：`<ChartRenderer chartType={activeChart} data={result.data} intent={msg.intent} />` + `<ChartSwitcher candidateCharts={pool} activeChart={activeChart} onChange={...} />`
- [x] 9.4 `onChange` 回调：`setMessages` 更新对应 msg 的 `activeChart` 字段（纯前端 state，不调 fetchSSE）
- [x] 9.5 候选池为空时（3+ dim 或 2+ metric）不渲染图表区，仅渲染表格 + 提示文案"维度过多，暂不支持自动可视化"
- [x] 9.6 删除现有 success 渲染态中"Recommended chart: {chartType}" 那行文字（被真正的图表替代）

## 10. 端到端测试

- [x] 10.1 前端单测：`chartPool.test.ts` 覆盖规则表所有分支 — 9 个测试用例通过
- [x] 10.2 前端单测：`chartOption.test.ts` 覆盖 8 种 chartType × fixture 数据 — 13 个测试用例通过
- [x] 10.3 前端单测：`BiArea.test.tsx` 更新现有测试 fixture（dimensions 改为 `{name,type}` 结构）— 已更新
- [x] 10.4 前端单测：`BiArea.test.tsx` 新增 chip 切换测试（点击 chip 后 activeChart 更新、不触发 fetchSSE）— 已通过
- [x] 10.5 前端单测：`BiArea.test.tsx` 新增 LLM 推荐值无效场景（chartType 不在候选池 → fallback + warning）— 已通过
- [x] 10.6 后端单测：`BiControllerTest`（或新增）验证 `IntentEvent` 输出 dimensions 含 type 字段 — `intentEventIncludesDimensionType` 测试通过，5/5 BiControllerTest 全部通过
- [ ] 10.7 E2E：1 dim 场景 "华东区销售额" → 验证渲染 bar 图、可切换到 pie — 需手动验证（需运行后端+前端+数据库+LLM 凭证）
- [ ] 10.8 E2E：2 dim 场景 "各区域各产品线销售额" → 验证渲染 grouped_bar、可切换 stacked_bar / heatmap，候选池不含 pie — 需手动验证
- [ ] 10.9 E2E：时间序列场景 "按月销售额趋势" → 验证渲染 line — 需手动验证
- [ ] 10.10 E2E：2 dim 含时间 "各区域按月销售额" → 验证渲染 line_multi — 需手动验证
- [ ] 10.11 E2E：0 dim 场景 "总销售额" → 验证渲染 KPI 卡片 — 需手动验证
- [ ] 10.12 E2E：3+ dim 场景 "各区域各产品线各签单人销售额" → 验证退化到表格 + 提示文案 — 需手动验证
