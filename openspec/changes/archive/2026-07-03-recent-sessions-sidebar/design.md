## Context

当前侧边栏 Sidebar.tsx 第 78 行有静态的 "Recent" 标题，下方无任何内容。ChatArea 和 BiArea 各自在组件内部通过 `useRef` 生成 sessionId，不会持久化。用户每次打开应用或切换模式后，之前的会话无法找回。

## Goals / Non-Goals

**Goals:**
- 侧边栏 Recent 区域展示最近会话列表（标题、模式图标、时间）
- 点击 Recent 项恢复对应 sessionId，LLM 可延续上下文
- 发送首条消息时自动保存会话到 localStorage
- 列表持久化到 localStorage，刷新不丢失
- 最多保留 20 条记录，超出时移除最旧的

**Non-Goals:**
- 不恢复历史消息内容（点击后是空白对话，仅 sessionId 复用）
- 不涉及后端 API 变更
- 不支持跨设备同步
- 不支持手动删除单条记录（MVP 阶段）
- 不支持会话重命名

## Decisions

### Decision 1: 状态提升到 ChatInterface

将 `recentSessions` 和 `activeSessionId` 状态放在 ChatInterface（Sidebar + ChatArea + BiArea 的共同父组件），通过 props 向下传递。

**理由**: ChatInterface 已经是三种组件的协调层，自然承载跨区域共享状态。避免引入 Context 或状态管理库的额外复杂度。

**替代方案**: 
- Context Provider：过度设计，当前只有 3 层传递
- 各 Area 自行管理 + EventEmitter：增加隐式耦合

### Decision 2: 自定义 hook `useRecentSessions`

封装 localStorage 读写逻辑到 `useRecentSessions` hook，返回 `[sessions, addSession]`。

```ts
interface RecentSession {
  id: string;
  title: string;    // 首条用户消息，截断到 30 字符
  mode: "chat" | "bi";
  timestamp: number; // Date.now()
}
```

**理由**: 关注点分离。localStorage 操作、序列化、截断逻辑集中在一处，组件只需消费数据和调用方法。

### Decision 3: sessionId 覆盖而非替换

ChatArea 和 BiArea 内部仍保留自己的 `sessionIdRef` 生成逻辑，但新增 `initialSessionId` prop。当 prop 有值时，使用 prop 值；无值时，使用自己生成的随机 ID。

**理由**: 最小侵入。不需要重构现有 sessionId 逻辑，"New Chat" / "BI" 按钮只需传 `null` 即可触发全新会话。

### Decision 4: 保存时机在首条消息发送时

在 `handleSendMessage`（Chat）和 `handleQuery`（BI）中，当 messages 数量从 0 变为 1 时（即首条消息），调用 `onFirstMessage(title)` 回调。

**理由**: 以用户的实际问题作为 Recent 标题更自然，而非 "New Chat 12:30" 这类无意义标签。也避免了空会话污染列表。

### Decision 5: localStorage key 设计

使用单一 key `ai4j-recent-sessions` 存储 JSON 数组。

**理由**: 单一 key 减少 localStorage 碎片。数组序列化/反序列化简单。无需迁移策略——key 不存在时退化为空数组。

## Risks / Trade-offs

- **[R] localStorage 容量限制（通常 5-10MB）** → 20 条记录每条约 100 字节，总计约 2KB，远低于限制
- **[R] 用户清除浏览器数据会丢失 Recent 列表** → 可接受，这是 lighter 方案明确的选择
- **[R] 隐私：localStorage 明文存储用户问题标题** → 仅存储首条消息前 30 字符，且为本地数据。后续可考虑不存标题仅存时间
- **[R] sessionId 复用但服务端无历史** → 对 Chat 模式，sessionId 目前仅透传给 LLM 作为 conversationId，服务端无持久化。LLM API 自身可能有时效限制（如 Gemini 的 conversation 有时效）。对 BI 模式，sessionId 用于澄清流程关联。此风险与当前行为一致，不引入新问题
