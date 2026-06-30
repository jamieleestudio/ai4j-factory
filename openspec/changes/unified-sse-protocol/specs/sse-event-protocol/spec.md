## ADDED Requirements

### Requirement: SSE 事件以 JSON envelope 传输
所有 SSE `data:` 行 SHALL 是一个 JSON 对象，包含 `type` 字段作为事件类型鉴别符。前端 SHALL 通过 `JSON.parse` 解析后按 `type` 分发处理，禁止依赖字符串前缀切片。

#### Scenario: 解析 JSON 事件
- **WHEN** 前端从 SSE 流读取一行 `data: {"type":"chunk","content":"hello"}`
- **THEN** 前端 `JSON.parse` 得到对象，按 `type: "chunk"` 路由到 chunk 处理逻辑，`content` 字段作为 token 文本

#### Scenario: 非法 JSON 降级
- **WHEN** SSE `data:` 行内容无法 `JSON.parse`
- **THEN** 前端跳过该行并记录警告，不中断流读取

### Requirement: 事件类型集合
envelope SHALL 支持以下 `type` 值：`status`、`intent`、`chunk`、`result`、`error`、`done`。每种类型有对应的字段契约。

#### Scenario: status 事件结构
- **WHEN** 后端推送进度事件
- **THEN** JSON 形如 `{"type":"status","stage":"<stage>","message":"<text>"}`，`stage` 标识管线阶段

#### Scenario: intent 事件结构
- **WHEN** 后端推送意图语义层事件
- **THEN** JSON 形如 `{"type":"intent","subject":"<subject>","metrics":[...],"dimensions":[...],"filters":[...]}`

#### Scenario: chunk 事件结构
- **WHEN** 后端推送文本 token
- **THEN** JSON 形如 `{"type":"chunk","content":"<token>"}`，`content` 为非空字符串

#### Scenario: result 事件结构
- **WHEN** 后端推送结构化结果
- **THEN** JSON 形如 `{"type":"result","chartType":"<type>","data":[...],"rowCount":<n>}`

#### Scenario: error 事件结构
- **WHEN** 后端推送错误
- **THEN** JSON 形如 `{"type":"error","message":"<text>"}`

#### Scenario: done 事件结构
- **WHEN** 后端推送流结束信号
- **THEN** JSON 形如 `{"type":"done"}`，无额外字段

### Requirement: 后端定义事件 DTO
后端 SHALL 用 record / sealed interface 定义事件 envelope 及各事件类型，通过 Jackson 序列化为 JSON 字符串后作为 SSE `data:` payload 推送。禁止用字符串拼接构造事件。

#### Scenario: 事件序列化
- **WHEN** 后端需要推送一个 chunk 事件
- **THEN** 构造 `ChunkEvent` record 实例，用 `ObjectMapper.writeValueAsString` 序列化为 `{"type":"chunk","content":"..."}`，作为 `sink.next` 的参数

### Requirement: 前端 discriminated union 类型
前端 SHALL 定义 `SseEvent` discriminated union 类型，按 `type` 字段区分各事件类型及对应字段。`fetchSSE` 的回调接口 SHALL 覆盖全部 6 种事件类型。

#### Scenario: 类型安全的回调分发
- **WHEN** 前端解析到 `type: "intent"` 事件
- **THEN** TypeScript 类型系统将其收窄为 `{ type: "intent"; subject: string; metrics: string[]; ... }`，调用 `onIntent` 回调

#### Scenario: 未知 type 降级
- **WHEN** 前端解析到 `type` 不在已知集合内
- **THEN** 前端跳过该事件，不触发任何回调，不中断流
