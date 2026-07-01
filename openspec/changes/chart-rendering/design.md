## Context

`chatbi-v1` 跑通了"自然语言 → 意图 → SQL → 洞察文本"链路，`unified-sse-protocol` 建立了 SSE JSON envelope，`clarification-flow` 增加了模糊输入引导。但前端 BI 的 success 渲染态仍停留在"洞察文本 + 通用表格 + 一行 Recommended chart 文字"，没有真正的图表可视化。

数据侧的信号其实已存在：
- `intent` 事件输出 `subject / metrics / dimensions / filters`
- `result` 事件输出 `chartType / data / rowCount`
- 语义层 `semantic/orders.json` 里每个 dimension 都有 `type` 字段（`STRING` / `TIME`）

但两个缺口阻断了图表渲染：
1. **`IntentEvent.dimensions` 只透传名字**（`["区域","产品线"]`），不透传 `type`，前端无法判定哪个维度是时间维度（`isTime`），也就无法在"line vs bar"之间做正确推断
2. **`chartType` 值域松散**：当前只有 `single_value/bar/line/pie` 4 种，无法表达"单指标 + 2 维度"场景需要的 `grouped_bar/stacked_bar/heatmap`

## Goals / Non-Goals

**Goals:**
- 让 BI success 渲染态真正画出图（ECharts 渲染 bar/pie/line/grouped_bar/stacked_bar/heatmap/line_multi + KPI 卡片）
- 用户能在候选图表间切换，切换是纯前端操作（无网络往返）
- LLM 给出推荐，前端按数据形状推断候选池，二者解耦
- 多维度场景（2 dim + 1 metric）的图表覆盖（grouped_bar/stacked_bar/heatmap）

**Non-Goals:**
- 不做多指标 combo 图（双 Y 轴 / 并列多指标）
- 不做 3+ 维度可视化
- 不做下钻 / 联动 / 跨图选择
- 不做切换状态持久化（刷新即重置为 LLM 推荐）
- 不做导出 / 截图 / 分享
- 不做暗色模式适配（V1 用 ECharts 默认主题）

## Decisions

### Decision 1: 图表库选 ECharts（一步到位含 heatmap），按需引入

引入 `echarts ^5.5.0`，按需引入 `echarts/core` + `BarChart/LineChart/PieChart/HeatmapChart` + 必要 components（`GridComponent/TooltipComponent/LegendComponent/VisualMapComponent/TitleComponent`）+ `CanvasRenderer`，gzip 后约 ~200KB。

**Alternatives considered:**
- **Recharts**：声明式、跟 Tailwind/React 风格一致、包小（~50KB），但**不支持 heatmap**。后续若要加 heatmap 仍需引 ECharts，两个库并存反而更重
- **Chart.js + react-chartjs-2**：包轻但同样不支持 heatmap，且中文生态弱
- **全量引入 ECharts**（`import * as echarts from 'echarts'`）：~1MB gzip，过度

**Why:** 用户明确"一步到位"含 heatmap；ECharts 是唯一能覆盖 V1 全部 8 种 chartType 的库；按需引入把体积压到 ~200KB，与 next.js bundle 预算兼容。

### Decision 2: 自包 `EChart.tsx` wrapper，不用 `echarts-for-react`

`echarts-for-react` 上次大版本 2023 年，React 19 兼容性未验证；自包 wrapper 仅 ~30 行，完全控制生命周期（init / setOption / resize / dispose），无第三方 wrapper 维护风险。

```tsx
function EChart({ option }: { option: EChartsOption }) {
  const ref = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts>();
  useEffect(() => {
    if (!ref.current) return;
    chartRef.current = echarts.init(ref.current);
    return () => chartRef.current?.dispose();
  }, []);
  useEffect(() => { chartRef.current?.setOption(option); }, [option]);
  useEffect(() => {
    const onResize = () => chartRef.current?.resize();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return <div ref={ref} className="w-full h-80" />;
}
```

**Alternatives considered:**
- `echarts-for-react`：省 30 行代码但引入 wrapper 维护风险
- 用 `echarts.use()` 全局注册 components 后散落调用：缺乏组件封装

**Why:** 自包组件代码量极少，避免 wrapper 跟 React 19 的兼容性悬而未决；生命周期完全可控（dispose 防内存泄漏、resize 适配侧栏开合）。

### Decision 3: 候选池推断走 P2 方案（前端规则），LLM 仅给推荐

`inferChartPool(intent)` 是前端纯函数，按 `ndim` + `isTime` + `nmetric` 推断候选图表列表；LLM 在 `InsightGenerationService` 的 prompt 里仍输出单个 `chartType` 作为推荐，但若推荐值 ∉ 候选池，前端 fallback 到候选池首项 + warning log。

**图表池规则表：**

| ndim | nmet | isTime | 候选池（有序，[0] 为 fallback） |
|------|------|--------|--------------------------------|------|
| 0 | 1 | - | `[single_value]` |
| 1 | 1 | No | `[bar, pie]` |
| 1 | 1 | Yes | `[line, bar]` |
| 2 | 1 | No | `[grouped_bar, stacked_bar, heatmap]` |
| 2 | 1 | Yes | `[line_multi, grouped_bar, stacked_bar]` |
| 3+ | 1 | - | `[]`（退化到表格） |
| any | 2+ | - | `[]`（退化到表格，V1 不支持多指标） |

**Alternatives considered:**
- **P1: LLM 输出候选列表**：LLM 知道用户语义（"占比"→ pie 也加入），但多一次输出字段、有幻觉风险（候选里写错类型名）
- **P3: 完全前端推断含推荐**：丢失 LLM 的语义信号（用户说"占比"前端无法识别 → 默认 bar）

**Why:** P2 各取所长——LLM 拿到自然语言上下文，做"用什么图"的语义判断最准；候选池是结构判断（基于数据形状），规则化最稳。契约只新增前端侧规则表，后端 SSE 输出不变（仍是单个 `chartType`）。切换是纯前端 state 更新，无网络往返。

### Decision 4: chartType 命名标准化为 8 种 snake_case 枚举（BREAKING）

| chartType | 适用数据形状 / 语义 |
|-----------|---------------------|
| `single_value` | 0 dim + 1 metric（KPI 卡片） |
| `bar` | 1 dim（非时间）+ 1 metric（分类对比） |
| `pie` | 1 dim（非时间）+ 1 metric（占比） |
| `line` | 1 dim（时间）+ 1 metric（时间序列） |
| `grouped_bar` | 2 dim + 1 metric（并列对比） |
| `stacked_bar` | 2 dim + 1 metric（堆叠占比） |
| `heatmap` | 2 dim + 1 metric（二维密度） |
| `line_multi` | 2 dim（含时间）+ 1 metric（多序列趋势） |

旧值 `single_value/bar/line/pie` 在 4 种 1 dim 场景下名字保持不变；新增 4 种覆盖 2 dim 场景。

**Alternatives considered:**
- 保留旧 4 种 + 新增 4 种（无 BREAKING）：LLM prompt 复杂、前端要兼容历史值；V1 内部项目无版本协商需求
- camelCase 命名（`groupedBar`）：与现有 `single_value` 风格不一致

**Why:** 内部项目前后端同步发布，BREAKING 一次性吸收最干净；snake_case 跟现有 `single_value` 风格一致。

### Decision 5: IntentEvent.dimensions 扩展为 `{name, type}` 对象数组（BREAKING）

从 `["区域","产品线"]` 改为 `[{name:"区域",type:"STRING"},{name:"产品线",type:"STRING"}]`。`type` 取自语义层 `Dimension.getType()`（`STRING` / `TIME`），让前端能判定 `isTime`（`intent.dimensions.some(d => d.type === "TIME")`）。

**Alternatives considered:**
- 前端硬编码"下单时间"为时间维度：脆弱，语义层一改就失效
- 后端在 `result` 事件里附带 `isTimeSeries: boolean`：把结构信号塞进结果事件，混淆关注点；多维度场景需要更细粒度信号（哪个 dim 是 TIME），单个 bool 不够

**Why:** `type` 是语义层的固有元数据，透传成本极低（后端 `Subject.getDimensions()` 已有 `type` 字段）；前端拿到的信号最准确（哪个维度是时间）。`isTime` 判定是候选池规则的关键输入，必须可靠。

### Decision 6: 切换状态为前端本地 state，不持久化

`BiMessage` 联合类型 success 状态新增 `activeChart?: ChartType` 字段，用户点 chip 时 `setMessages` 更新该字段。默认 `activeChart = chartType`（LLM 推荐）；若推荐 ∉ 候选池则 fallback 到候选池[0]。

**Alternatives considered:**
- 持久化到 `sessionStorage`：切换是临时探索行为，重新提问理应回到推荐
- 持久化到后端：跨设备同步，V1 不需要

**Why:** 切换是用户当下探索"换个视角看"的临时操作，不是对图表类型的偏好设定；刷新 / 重新提问回到 LLM 推荐符合直觉。无网络往返、无存储逻辑，实现最简。

### Decision 7: 多维度场景禁止 pie（不降维）

`ndim=2` 候选池为 `[grouped_bar, stacked_bar, heatmap]`（或含 `line_multi` 若 isTime），不含 `pie`。即使 LLM 误推荐 `pie`，前端 fallback 到候选池首项。

**Alternatives considered:**
- 允许 pie 并前端按 dim[0] 自动聚合 dim[1]：丢失 dim[1] 的细分信息，产生误导（"华东+华北" 饼图看不到产品线分布）
- 允许 pie 并提示用户"已按 dim[0] 聚合"：增加 UI 复杂度，且用户难以判断聚合是否合理

**Why:** 用户明确禁止降维。多维度场景下 pie 是错误图表类型，候选池不放比让用户切过去看到误导图更好。

### Decision 8: 字段映射规则以纯函数实现，配合快照测试

`buildOption(chartType, data, intent)` 是纯函数，输入 chartType + data + intent，输出 `EChartsOption`。每个 chartType 对应一个 switch case，按规则构造 xAxis / yAxis / series / visualMap 等。

字段映射规则：

| chartType | X 轴 | Y 轴 | series | 备注 |
|-----------|------|------|--------|------|
| `single_value` | - | - | - | 不走 ECharts，渲染 KPI 卡片 |
| `bar` | dim[0] values | value | `[{type:'bar', data: metric[0] values}]` | |
| `pie` | - | - | `[{type:'pie', data: rows.map(r => ({name:r[dim[0]], value:r[metric[0]]}))}]` | |
| `line` | dim[0] values（时间） | value | `[{type:'line', data: metric[0] values}]` | |
| `grouped_bar` | dim[0] unique | value | dim[1] unique values.map(v => `{name:v, type:'bar', data:...})` | 并列 |
| `stacked_bar` | dim[0] unique | value | 同 grouped_bar，但 `stack:'total'` | 堆叠 |
| `heatmap` | dim[0] unique | dim[1] unique | `[{type:'heatmap', data: rows.map(r => [x_idx, y_idx, metric[0]])}]` + visualMap | |
| `line_multi` | dim[0]（TIME） | value | dim[1] unique values.map(v => `{name:v, type:'line', data:...})` | 多序列 |

**Why:** 纯函数 + 快照测试是最易测的组合——给定 fixture 数据（Case A/B/C）和 chartType，断言输出 option JSON 完全确定。ECharts 渲染本身不需要测（库已测），只测 option 构造逻辑。

## Risks / Trade-offs

- **[BREAKING: IntentEvent.dimensions 结构变更]** 旧前端不解析 `type` 字段会崩 → 内部项目前后端同步发布，无版本协商；前端 `IntentPayload` 类型同步改、`ThinkingBlock` 渲染改 `dim.name`、测试 fixture 同步更新
- **[BREAKING: chartType 值域扩展]** 旧前端遇到 `grouped_bar` 等新值会 fallback 到表格（候选池为空）→ 同上，同步发布；`BiArea` 渲染时若 `activeChart` 不在候选池内，统一 fallback 到候选池[0]
- **[ECharts 包体积 ~200KB]** next.js bundle 增加 ~200KB gzip → 按需引入已最小化；后续可考虑动态 import（`next/dynamic`）把 EChart 组件做成 lazy chunk，首屏不阻塞
- **[LLM 推荐错 chartType]** LLM 输出 `pie` 但数据是 2 维度 → 前端候选池校验，fallback 到 `grouped_bar` + warning log；不阻塞用户体验
- **[ECharts 与 React 19 / Next 16 兼容]** ECharts 核心 API 稳定（5.x 已支持 React 18），自包 wrapper 控制生命周期 → 实施时先做技术 spike 验证 `echarts.init` 在 Next 16 client component 下能跑
- **[SSR 问题]** ECharts 依赖 DOM，Next 16 RSC 下不能服务端渲染 → `EChart.tsx` 用 `'use client'` 指令 + `useEffect` 中 init，避免 SSR 访问 `window`/`document`
- **[多维度数据 dim[1] 值过多]** 2 dim 场景下 dim[1] 唯一值过多（如 50 个产品线）→ grouped_bar 会变成 50 个 series 的密集图；V1 不限制，后续可加 "dim[1] 唯一值 > N 时退化到 heatmap" 的规则
- **[依赖 unified-sse-protocol 归档]** `chart-rendering` 修改 `sse-event-protocol` 的 `intent` 事件结构，依赖 `unified-sse-protocol` 先归档建立基础 envelope spec → 实施顺序上 `chart-rendering` 必须在 `unified-sse-protocol` 完成后进行
- **[与 clarification-flow 合并冲突]** 两个 change 都修改 `BiController.java` 和 `IntentEvent.java`，需协调合并顺序 → 建议 `clarification-flow` 先归档（36/39 接近完成），`chart-rendering` 在其基础上分支

## Migration Plan

1. 先完成 `unified-sse-protocol`（剩余 4 个任务）和 `clarification-flow`（剩余 3 个任务），归档后 `sse-event-protocol` 与 `bi-streaming` 基础 spec 建立
2. 后端轻改先行（3 文件 ~30 行）：
   - `IntentEvent.java`：dimensions 改为 `List<DimensionRef>`
   - `BiController.toIntentEvent()`：填 type
   - `InsightGenerationService.buildInsightPrompt()`：图类型表更新
3. 前端集成 ECharts：
   - `package.json` 加 `echarts ^5.5.0`
   - 新增 `lib/chartTypes.ts` / `chartPool.ts` / `chartOption.ts` + 单测
   - 新增 `components/EChart.tsx` / `ChartRenderer.tsx` / `ChartSwitcher.tsx`
   - 改 `BiArea.tsx`：success 渲染态插入图表区 + chips
   - 改 `utils/fetchSSE.ts`：`IntentPayload.dimensions` 类型同步
4. 端到端测试：
   - 1 dim 场景："华东区销售额" → bar/pie 切换
   - 2 dim 场景："各区域各产品线销售额" → grouped_bar/stacked_bar/heatmap 切换
   - 时间序列场景："按月销售额趋势" → line
   - 多维度时间场景："各区域按月销售额" → line_multi
   - 0 dim 场景："总销售额" → KPI 卡片
   - 3+ dim 场景："各区域各产品线各签单人销售额" → 退化到表格
5. 无数据迁移（纯前端渲染逻辑 + SSE 结构变更）

## Open Questions

- `single_value` 场景下 KPI 卡片是否显示维度名 / 指标名？倾向显示指标名作为标题（如"销售额"），数值格式化（千分位 / 货币符号）。具体格式留待实施时决定
- `line` 图的 X 轴是否启用 `type: 'time'`（真时间轴，自动处理稀疏数据）还是 `type: 'category'`（按数据点等距）？倾向 `category`，V1 数据点有限，等距更直观
- ECharts 主题是否需要适配 `next-themes` 暗色模式？V1 用默认主题，后续若 UI 反馈暗色下图表不可读，再加 `theme` prop
