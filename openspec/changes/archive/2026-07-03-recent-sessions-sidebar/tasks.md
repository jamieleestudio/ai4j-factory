## 1. 基础 Hook

- [x] 1.1 创建 `useRecentSessions.ts` hook，定义 `RecentSession` 类型和 localStorage 读写逻辑
- [x] 1.2 实现 `addSession` 方法：新会话 prepend 到列表，已存在会话更新 timestamp 和 title，超过 20 条时裁剪
- [x] 1.3 实现相对时间格式化工具函数（如"3分钟前"、"1小时前"）

## 2. 状态提升

- [x] 2.1 在 `ChatInterface.tsx` 中使用 `useRecentSessions` hook，新增 `activeSessionId` state
- [x] 2.2 将 `recentSessions`、`onRecentClick`、`onFirstMessage` 传递给 Sidebar
- [x] 2.3 将 `activeSessionId` 和 `onFirstMessage` 回调传递给 ChatArea 和 BiArea

## 3. Sidebar 改造

- [x] 3.1 Sidebar 接收 `recentSessions` 和 `onRecentClick` props
- [x] 3.2 Recent 区域渲染会话列表，每项显示模式图标（chat: MessageSquare, bi: BarChart3）、标题、相对时间
- [x] 3.3 列表为空时隐藏 Recent 区域
- [x] 3.4 当前活跃会话高亮显示
- [x] 3.5 "New Chat" 和 "Data Warehouse BI" 按钮点击时清除 `activeSessionId`

## 4. ChatArea 适配

- [x] 4.1 ChatArea 新增 `initialSessionId` 和 `onFirstMessage` props
- [x] 4.2 sessionId 逻辑：有 `initialSessionId` 时使用它，否则生成新的随机 ID
- [x] 4.3 首条消息发送时调用 `onFirstMessage(content)`

## 5. BiArea 适配

- [x] 5.1 BiArea 新增 `initialSessionId` 和 `onFirstMessage` props
- [x] 5.2 sessionId 逻辑：有 `initialSessionId` 时使用它，否则生成新的随机 ID
- [x] 5.3 首条消息发送时调用 `onFirstMessage(content)`
