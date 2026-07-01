## MODIFIED Requirements

### Requirement: 事件类型集合
envelope SHALL 支持以下 `type` 值：`status`、`intent`、`chunk`、`result`、`error`、`done`、`clarification`。每种类型有对应的字段契约。

#### Scenario: status 事件结构
- **WHEN** 后端推送进度事件
- **THEN** JSON 形如 `{"type":"status","stage":"<stage>","message":"<text>"}`，`stage` 标识管线阶段

#### Scenario: intent 事件结构
- **WHEN** 后端推送意图语义层事件
- **THEN** JSON 形如 `{"type":"intent","subject":"<subject>","metrics":[...],"dimensions":[{"name":"<name>","type":"<STRING|TIME>"}],"filters":[{"dimension":"<name>","operator":"<op>","value":"<val>"}]}`，`dimensions` 为对象数组，每个对象含 `name`（维度名）和 `type`（维度类型，取自语义层 `Dimension.type`）字段

#### Scenario: chunk 事件结构
- **WHEN** 后端推送文本 token
- **THEN** JSON 形如 `{"type":"chunk","content":"<token>"}`，`content` 为非空字符串

#### Scenario: result 事件结构
- **WHEN** 后端推送结构化结果
- **THEN** JSON 形如 `{"type":"result","chartType":"<type>","data":[...],"rowCount":<n>}`，`chartType` 取值为 8 种标准枚举之一（详见 `bi-streaming` capability 的"BI 结构化结果推送" requirement）

#### Scenario: clarification 事件结构
- **WHEN** 后端判定用户输入需要澄清
- **THEN** JSON 形如 `{"type":"clarification","sessionId":"<uuid>","message":"<text>","options":[{"label":"<label>","value":"<value>","description":"<text>"}]}`

#### Scenario: error 事件结构
- **WHEN** 后端推送错误
- **THEN** JSON 形如 `{"type":"error","message":"<text>"}`

#### Scenario: done 事件结构
- **WHEN** 后端推送流结束信号
- **THEN** JSON 形如 `{"type":"done"}`，无额外字段
