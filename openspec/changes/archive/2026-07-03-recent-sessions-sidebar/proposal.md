## Why

侧边栏已有 "Recent" 占位区域但没有任何内容。用户每次使用都是全新会话，无法快速回到之前的对话。需要让 Recent 区域展示最近的会话记录，点击后恢复对应的 sessionId，使 LLM 能够延续之前的上下文。

## What Changes

- 新增 `useRecentSessions` hook，基于 localStorage 管理最近会话列表
- Sidebar 的 Recent 区域渲染最近会话列表，按时间倒序排列
- 点击 Recent 项切换到对应模式（chat/bi）并设置 sessionId 覆盖值
- ChatArea 和 BiArea 支持接收外部传入的 sessionId 覆盖值
- 发送首条消息时自动将会话保存到 Recent 列表
- "New Chat" / "Data Warehouse BI" 按钮点击时清除 sessionId 覆盖，开始全新会话

## Capabilities

### New Capabilities
- `recent-sessions`: 侧边栏最近会话列表，基于 localStorage 存储会话元数据（id、标题、模式、时间戳），支持点击恢复 sessionId

### Modified Capabilities
<!-- No existing specs are modified -->

## Impact

- `Sidebar.tsx` — Recent 区域从静态占位变为动态列表
- `ChatInterface.tsx` — 新增 recentSessions 和 activeSessionId 状态管理
- `ChatArea.tsx` — 接收可选的 sessionId 覆盖 prop
- `BiArea.tsx` — 接收可选的 sessionId 覆盖 prop
- 新增 `useRecentSessions.ts` hook
