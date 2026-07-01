## Why

BI 查询返回的 `result` 事件已包含 `chartType` 和 `data`，但前端 `BiArea.tsx` 仅渲染一行文字 "Recommended chart: bar" + 通用表格，没有任何真正的图表可视化。用户问"华东区各产品线销售额"，得到的体验是看到表格 + 一行文字提示，无法直观感受数据。同时 `chartType` 当前只有 4 种松散值（`single_value/bar/line/pie`），无法表达"单指标多维度"场景（如 2 维度需要 `grouped_bar/stacked_bar/heatmap`）。

## What Changes

- **新增前端图表渲染能力**：集成 ECharts（按需引入 ~200KB gzip），新增 `EChart.tsx` wrapper、`ChartRenderer.tsx`、`ChartSwitcher.tsx` 三个组件，在 `BiArea.tsx` 的 success 渲染态插入图表区 + chips 切换 UI
- **前端按数据形状推断图表候选池** **NEW**：新增 `lib/chartPool.ts`，输入 `intent.dimensions` + `intent.metrics`，按 `ndim` + `isTime` + `nmetric` 规则推断候选图表类型列表（候选池）；LLM 推荐的 `chartType` 若不在候选池内则 fallback 到候选池首项 + 警告日志
- **统一 chartType 命名为 8 种 snake_case 标准值** **BREAKING**：`single_value`、`bar`、`pie`、`line`、`grouped_bar`、`stacked_bar`、`heatmap`、`line_multi`；后端 `InsightGenerationService` 的 LLM prompt 图类型表同步更新
- **`IntentEvent.dimensions` 结构扩展为对象数组** **BREAKING**：从 `["区域","产品线"]` 改为 `[{name:"区域",type:"STRING"},{name:"产品线",type:"STRING"}]`，让前端能判定 `isTime`（存在 `type:"TIME"` 的维度）；后端 `BiController.toIntentEvent()` 从 `Subject.getDimensions()` 取 type 一起填入
- **图表切换为纯前端 state，不持久化**：用户点 chip 切换图表时只更新本地 `activeChart` 字段，不触发网络请求、不写 sessionStorage；重新提问 / 刷新页面回到 LLM 推荐
- **V1 范围限定为单指标**（`nmetric=1`）+ 0/1/2 维度：3+ 维度或 2+ 指标时候选池为空，前端退化到只显示表格 + 提示"维度过多，暂不支持自动可视化"
- **多维度场景禁止 pie**：`ndim=2` 候选池为 `[grouped_bar, stacked_bar, heatmap]`，不允许降维到饼图（避免前端按 dim[0] 聚合 dim[1] 产生误导）
- **BI 渲染态视觉容器最小化**：Insight 文本（streaming + success）改为纯文字无边框、无 "Insight" 小标签、无闪烁光标；Data Table 限高 `max-h-80`（约 10 行）+ 内部滚动；`single_value` 场景不渲染 Data Table（KPI 卡已表达，表格冗余）

## Capabilities

### New Capabilities

- `chart-rendering`: 前端按数据形状推断图表候选池、根据 `chartType` + `intent` + `data` 构造 ECharts option、用户在 chips 间切换图表类型的能力

### Modified Capabilities

- `bi-streaming`: `result` 事件的 `chartType` 字段值域标准化为 8 种 snake_case 枚举值；多维度场景下 LLM 不应推荐 `pie`
- `sse-event-protocol`: `intent` 事件的 `dimensions` 字段从字符串数组扩展为 `{name, type}` 对象数组（type 取自语义层 dimension 定义）

## Impact

- **后端**：
  - `InsightGenerationService.java`：`buildInsightPrompt()` 的图类型表更新为 8 种标准值（含适用场景说明，如 `grouped_bar: 2 维度并列对比`）
  - `IntentEvent.java`：`dimensions` 字段类型从 `List<String>` 改为 `List<DimensionRef>`（新增 record `DimensionRef(String name, String type)`）
  - `BiController.java`：`toIntentEvent()` 从 `Subject.getDimensions()` 取 `type` 一起填入
  - 改动量：3 个文件，约 30 行
- **前端**：
  - 新增依赖：`echarts ^5.5.0`（按需引入 `echarts/core` + 4 种 chart + 必要 components）
  - 新增 `lib/chartTypes.ts`：`ChartType` union（8 种）+ `chartLabel()` 显示文案
  - 新增 `lib/chartPool.ts`：`inferChartPool(intent)` 纯函数 + 单测
  - 新增 `lib/chartOption.ts`：`buildOption(chartType, data, intent)` 纯函数 + 单测（快照测试各 chartType × fixture 数据 → ECharts option）
  - 新增 `components/EChart.tsx`：自包 ECharts wrapper（生命周期管理：init / setOption / resize / dispose）
  - 新增 `components/ChartRenderer.tsx`：根据 `chartType` 分发到 `EChart` 或 KPI 卡片（`single_value`）
  - 新增 `components/ChartSwitcher.tsx`：chips 切换 UI（推荐项实心填充、其他 outline）
  - 改 `BiArea.tsx`：success 渲染态插入 `<ChartRenderer>` + `<ChartSwitcher>`；`BiMessage` 联合类型 success 状态新增 `activeChart?: ChartType` 本地字段
  - 改 `utils/fetchSSE.ts`：`IntentPayload.dimensions` 类型从 `string[]` 改为 `{name: string; type: string}[]`
  - 改 `BiArea.tsx`：`ThinkingBlock` 渲染 dimensions 时改为 `dim.name`
  - 改 `BiArea.test.tsx`：测试 fixture 同步更新 dimensions 结构
  - 改动量：6 个新增 + 4 个修改 + 2 个测试
- **API 变更** **BREAKING**：
  - SSE `intent` 事件 `dimensions` 字段从 `["区域"]` 变为 `[{name:"区域",type:"STRING"}]`
  - SSE `result` 事件 `chartType` 字段值域从 4 种扩展为 8 种 snake_case 标准值
- **依赖**：
  - 依赖 `unified-sse-protocol` 已归档（`sse-event-protocol` 基础 envelope spec 已建立，`intent` 事件 envelope 已定义）；实施顺序上 `chart-rendering` 应在 `unified-sse-protocol` 完成后进行
  - 与 `clarification-flow`（in-progress, 36/39）正交，无相互依赖；但二者都修改 `BiController.java` 和 `IntentEvent`，需协调合并
- **范围排除**：
  - 不做图表导出 / 截图 / 嵌入分享
  - 不做多指标 combo 图（双 Y 轴）
  - 不做图表交互的下钻 / 联动
  - 不持久化用户切换选择（刷新即重置为 LLM 推荐）
  - 不做 3+ 维度可视化（退化到表格）
  - 不做自定义主题 / 暗色模式适配（V1 用 ECharts 默认主题；暗色模式后续根据 `next-themes` 适配）
