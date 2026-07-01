## ADDED Requirements

### Requirement: clarification 事件类型
envelope SHALL 支持 `clarification` 事件类型，用于在意图提取阶段判定用户输入需要澄清时，向前端推送澄清请求。`clarification` 事件 SHALL 包含 `sessionId`、`message`、`options` 字段，不含 SQL 或查询数据。

#### Scenario: clarification 事件结构
- **WHEN** 后端判定用户输入需要澄清
- **THEN** JSON 形如 `{"type":"clarification","sessionId":"<uuid>","message":"<text>","options":[{"label":"<label>","value":"<value>","description":"<text>"}]}`

#### Scenario: clarification 事件后跟随 done
- **WHEN** 后端推送 `clarification` 事件
- **THEN** 后端在 clarification 之后推送 `done` 事件并关闭 SSE 连接，不推送 `intent`、`chunk`、`result` 事件

#### Scenario: options 字段为非空数组
- **WHEN** `clarification` 事件的 `options` 字段
- **THEN** `options` SHALL 是非空数组，每个元素含 `label`（展示文案）、`value`（下一轮 question 传入值）、`description`（可选说明文本）

### Requirement: 前端解析 clarification 事件
前端 discriminated union SHALL 额外包含 `clarification` 事件类型，`fetchSSE` 的回调接口 SHALL 覆盖 `clarification`。

#### Scenario: clarification 回调分发
- **WHEN** 前端解析到 `type: "clarification"` 事件
- **THEN** TypeScript 类型系统将其收窄为 `{ type: "clarification"; sessionId: string; message: string; options: ClarificationOption[] }`，调用 `onClarification` 回调

#### Scenario: 前端持有 sessionId 用于下一轮
- **WHEN** 前端收到 `clarification` 事件
- **THEN** 前端持有 `sessionId`，在用户点 chip 或重新打字时作为下一轮请求的 `sessionId` 参数传入
