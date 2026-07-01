## MODIFIED Requirements

### Requirement: 前端 Chat 使用统一 SSE 订阅工具
前端 Chat 组件 SHALL 使用统一的 `subscribeSSE` 工具函数订阅 SSE 流，禁止手搓 `new EventSource` 调用。统一工具确保 SSE 帧解析、错误处理、连接生命周期管理在 BI 和 Chat 之间一致。

#### Scenario: 订阅 Chat 流
- **WHEN** 用户在 Chat 页面发送消息
- **THEN** 调用 `subscribeSSE(url, { onChunk, onDone, onError })`，返回的订阅对象存入 ref，组件卸载时调用 `close()`

#### Scenario: 流结束
- **WHEN** 收到 `done` 事件
- **THEN** 调用 `close()` 主动关闭连接，停止加载状态
