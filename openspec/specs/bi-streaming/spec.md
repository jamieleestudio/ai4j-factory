## ADDED Requirements

### Requirement: BI 查询进度事件推送
BI 查询接口 SHALL 在执行管线的各个阶段推送进度事件，告知前端当前处理状态。

#### Scenario: 意图提取阶段
- **WHEN** 系统开始从用户问题中提取查询意图
- **THEN** 前端收到 `[progress]` 事件，显示"正在分析你的问题..."等提示

#### Scenario: SQL 执行阶段
- **WHEN** SQL 查询已生成并开始执行
- **THEN** 前端收到 `[progress]` 事件，显示"正在查询数据库..."等提示

#### Scenario: 洞察生成阶段
- **WHEN** 数据查询完成，开始调用 LLM 生成洞察
- **THEN** 前端收到 `[progress]` 事件，显示查询到的记录数和"正在生成洞察..."等提示

### Requirement: BI 洞察文本逐 token 流式输出
系统 SHALL 在洞察生成阶段将 LLM 输出的文本以 token 级别逐片推送到前端。

#### Scenario: 洞察文本逐字显示
- **WHEN** LLM 正在根据查询结果生成洞察文本
- **THEN** 前端收到 `[chunk]` 事件，每个 chunk 追加到洞察文本末尾，用户看到逐字生成效果

### Requirement: BI 结构化结果推送
洞察生成完成后，系统 SHALL 推送包含图表类型和数据表格的结构化结果。

#### Scenario: 推送图表类型和数据
- **WHEN** LLM 完成洞察生成
- **THEN** 前端收到 `[result]` 事件，包含 JSON 格式的 `chartType` 和 `data` 字段，前端渲染图表推荐和数据表格

### Requirement: BI 端点返回 SSE 流
BI 查询端点 SHALL 返回 `text/event-stream` 类型，使用 POST 方法接收 JSON 请求体，以 SSE 格式流式返回结果。

#### Scenario: POST SSE 响应
- **WHEN** `POST /api/bi/query` 被调用，body 包含 `question`、`credentialId`、`modelName`
- **THEN** 响应 Content-Type 为 `text/event-stream`，按序推送 `[progress]`、`[chunk]`、`[result]` 事件

### Requirement: 前端 BI 使用 ReadableStream 读取 SSE
前端 BI 组件 SHALL 使用 `fetch` + `ReadableStream` 方式读取 SSE 流，而非 `EventSource`。

#### Scenario: POST SSE 读取
- **WHEN** 前端发起 BI 查询
- **THEN** 使用 `fetch` POST 请求，通过 `response.body.getReader()` 逐行读取 SSE 数据，根据前缀 `[progress]`、`[chunk]`、`[result]` 分别处理

#### Scenario: 流式读取完成
- **WHEN** SSE 流结束（reader 返回 done）
- **THEN** 前端停止加载状态，最终结果渲染完成

#### Scenario: 读取过程出错
- **WHEN** 网络错误或服务端异常导致流中断
- **THEN** 前端显示错误状态，已接收的部分内容保留显示
