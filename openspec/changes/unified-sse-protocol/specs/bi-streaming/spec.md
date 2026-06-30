## MODIFIED Requirements

### Requirement: BI 查询进度事件推送
BI 查询接口 SHALL 在执行管线的各个阶段推送 `status` 事件（JSON envelope），告知前端当前处理状态。`status` 事件 SHALL 包含 `stage`（管线阶段标识）和 `message`（展示文案）字段。

#### Scenario: 意图提取阶段
- **WHEN** 系统开始从用户问题中提取查询意图
- **THEN** 前端收到 `{"type":"status","stage":"analyzing","message":"正在分析你的问题..."}` 事件，显示进度文案

#### Scenario: SQL 执行阶段
- **WHEN** SQL 查询已生成并开始执行
- **THEN** 前端收到 `{"type":"status","stage":"querying","message":"正在查询数据库..."}` 事件

#### Scenario: 洞察生成阶段
- **WHEN** 数据查询完成，开始调用 LLM 生成洞察
- **THEN** 前端收到 `{"type":"status","stage":"insight","message":"查询到 N 条记录，正在生成洞察..."}` 事件，`N` 为实际记录数

### Requirement: BI 洞察文本逐 token 流式输出
系统 SHALL 在洞察生成阶段将 LLM 输出的文本以 token 级别逐片推送到前端，每个 token 作为一个 `chunk` 事件（JSON envelope）推送。推送前 SHALL 剥离 `<<CHART:>>` 标记，前端收到的 chunk 内容 SHALL 不含该标记。

#### Scenario: 洞察文本逐字显示
- **WHEN** LLM 正在根据查询结果生成洞察文本
- **THEN** 前端收到 `{"type":"chunk","content":"<token>"}` 事件，`content` 追加到洞察文本末尾，用户看到逐字生成效果

#### Scenario: 图表类型标记对前端不可见
- **WHEN** LLM 输出的某段文本包含 `<<CHART:bar>>` 标记
- **THEN** 后端在推送 `chunk` 事件前剥离该标记，前端 `content` 字段不含 `<<CHART:>>` 子串

### Requirement: BI 结构化结果推送
洞察生成完成后，系统 SHALL 推送 `result` 事件（JSON envelope），包含图表类型、数据表格、记录数。`result` 事件 SHALL 采用窄口径，仅含 `chartType`、`data`、`rowCount` 三个字段，不包含 SQL 和意图（意图由 `intent` 事件单独推送）。

#### Scenario: 推送图表类型和数据
- **WHEN** LLM 完成洞察生成
- **THEN** 前端收到 `{"type":"result","chartType":"<type>","data":[...],"rowCount":<n>}` 事件，前端渲染图表推荐和数据表格

#### Scenario: chartType 由后端从完整文本解析
- **WHEN** 洞察流结束
- **THEN** 后端从累积的完整 `fullText` 调用 `extractChartType` 得到 `chartType`，放入 `result` 事件；若 LLM 未输出标记，`chartType` 默认为 `"bar"`

### Requirement: BI 端点返回 SSE 流
BI 查询端点 SHALL 返回 `text/event-stream` 类型，使用 POST 方法接收 JSON 请求体，以 SSE 格式流式返回 JSON envelope 事件。

#### Scenario: POST SSE 响应
- **WHEN** `POST /api/bi/query` 被调用，body 包含 `question`、`credentialId`、`modelName`
- **THEN** 响应 Content-Type 为 `text/event-stream`，按序推送 `status`、`intent`、`chunk`、`result`、`done` 事件（每个 `data:` 行为 JSON 对象）

### Requirement: 前端 BI 使用 ReadableStream 读取 SSE
前端 BI 组件 SHALL 使用 `fetch` + `ReadableStream` 方式读取 SSE 流，通过 `JSON.parse` 解析每个 `data:` 行。

#### Scenario: POST SSE 读取
- **WHEN** 前端发起 BI 查询
- **THEN** 使用 `fetch` POST 请求，通过 `response.body.getReader()` 逐行读取 SSE 数据，`JSON.parse` 后按 `type` 分发到对应回调

#### Scenario: 流式读取完成
- **WHEN** SSE 流收到 `done` 事件或 reader 返回 done
- **THEN** 前端停止加载状态，最终结果渲染完成

#### Scenario: 读取过程出错
- **WHEN** 收到 `error` 事件，或网络错误 / 服务端异常导致流中断
- **THEN** 前端显示错误状态，已接收的部分内容保留显示

## ADDED Requirements

### Requirement: BI 意图语义层作为 thinking 推送
BI 查询接口 SHALL 在意图提取完成后、SQL 执行前，推送 `intent` 事件，将意图语义层暴露给前端作为 thinking 内容展示。

#### Scenario: 推送意图事件
- **WHEN** `IntentExtractionService.extract()` 返回 `QueryIntent`
- **THEN** 后端推送 `{"type":"intent","subject":"<subject>","metrics":[...],"dimensions":[...],"filters":[...]}` 事件，字段从 `QueryIntent` 透传

#### Scenario: 前端展示意图 thinking
- **WHEN** 前端收到 `intent` 事件
- **THEN** 在 thinking 区渲染意图语义层（subject、metrics、dimensions、filters），与 `status` 进度文案一并展示

### Requirement: BI 流结束推送 done 事件
BI 查询接口 SHALL 在 `result` 事件之后推送 `done` 事件，作为流结束的显式信号。

#### Scenario: 正常完成
- **WHEN** 洞察生成和结果推送全部完成
- **THEN** 后端推送 `{"type":"done"}` 后关闭 SSE 连接

#### Scenario: 异常完成
- **WHEN** 管线任意阶段抛出异常
- **THEN** 后端推送 `{"type":"error","message":"..."}` 后推送 `done` 并关闭连接
