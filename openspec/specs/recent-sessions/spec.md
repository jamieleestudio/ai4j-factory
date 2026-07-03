## ADDED Requirements

### Requirement: 最近会话列表展示
侧边栏 SHALL 在 "Recent" 标题下方展示最近会话列表，按时间倒序排列，每项显示会话标题、模式图标和相对时间。

#### Scenario: 列表有记录时展示
- **WHEN** localStorage 中存在最近会话记录
- **THEN** 侧边栏 Recent 区域按时间倒序渲染每条会话，chat 模式显示消息气泡图标，bi 模式显示图表图标，标题截断到 30 字符，时间显示为相对格式（如"3分钟前"）

#### Scenario: 列表为空时隐藏 Recent 标题
- **WHEN** localStorage 中无任何最近会话记录
- **THEN** Recent 标题和列表区域不显示

#### Scenario: 列表超过 20 条时裁剪
- **WHEN** 最近会话记录超过 20 条
- **THEN** 仅保留最新的 20 条，旧记录被移除

### Requirement: 会话自动保存
系统 SHALL 在用户发送首条消息时自动将会话保存到 localStorage 的最近会话列表中。

#### Scenario: Chat 模式首条消息保存
- **WHEN** 用户在 Chat 模式下发送首条消息（messages 从空变为有内容）
- **THEN** 系统将当前会话 `{ id: sessionId, title: 首条消息截断至30字符, mode: "chat", timestamp: Date.now() }` 保存到 localStorage，并更新侧边栏列表

#### Scenario: BI 模式首条消息保存
- **WHEN** 用户在 BI 模式下发送首条查询（messages 从空变为有内容）
- **THEN** 系统将当前会话 `{ id: sessionId, title: 首条查询截断至30字符, mode: "bi", timestamp: Date.now() }` 保存到 localStorage，并更新侧边栏列表

#### Scenario: 同一会话多次发送不重复保存
- **WHEN** 会话已存在于 Recent 列表中，用户发送后续消息
- **THEN** 更新该会话的 `timestamp` 和 `title`（最新消息内容），而非创建新记录

### Requirement: 点击 Recent 项恢复会话
系统 SHALL 支持用户点击 Recent 列表项来恢复对应会话的 sessionId，并切换到正确的模式。

#### Scenario: 点击 Recent 项切换到对应模式
- **WHEN** 用户点击某个 mode 为 "bi" 的 Recent 项
- **THEN** 主区域切换到 BI 模式，并使用该会话的 sessionId 作为当前会话 ID

#### Scenario: 点击 Recent 项后发送消息延续上下文
- **WHEN** 用户点击 Recent 项进入某个会话后发送消息
- **THEN** 请求携带该会话的 sessionId，LLM 可延续之前的对话上下文

#### Scenario: 恢复后不展示历史消息
- **WHEN** 用户点击 Recent 项恢复会话
- **THEN** 消息列表为空（不恢复历史消息），仅 sessionId 被复用

### Requirement: 新建会话清除覆盖
系统 SHALL 在用户点击 "New Chat" 或 "Data Warehouse BI" 按钮时清除 sessionId 覆盖，开始全新会话。

#### Scenario: 点击 New Chat 开始全新会话
- **WHEN** 用户当前在某 Recent 会话中，点击 "New Chat" 按钮
- **THEN** sessionId 覆盖被清除，系统生成新的随机 sessionId，消息列表清空

#### Scenario: 点击 BI 按钮开始全新会话
- **WHEN** 用户当前在某 Recent 会话中，点击 "Data Warehouse BI" 按钮
- **THEN** sessionId 覆盖被清除，系统生成新的随机 sessionId，消息列表清空
