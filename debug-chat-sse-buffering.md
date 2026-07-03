# Debug Session: chat-sse-buffering

Status: [OPEN]
Started: 2026-07-02

## Symptom
- Chat SSE 目前表现为内容突然整体出现，而不是一句一句或 token-by-token 增量推送。

## Scope
- 先只采集证据，不修改业务逻辑。
- 优先排查后端是否真实分块发送、是否被 Spring MVC / Servlet 容器 / 中间层缓冲、以及前端是否按流式消费。

## Falsifiable Hypotheses
1. `ChatService` 下游模型流本身没有逐段产出，而是先聚合后一次性吐出完整文本。
2. 后端虽然返回了 `text/event-stream`，但实际 HTTP 响应被某个层面缓冲，导致客户端直到缓冲完成才看到内容。
3. 前端请求方式或读取方式不是真正的流式消费，导致浏览器端收到数据后没有即时渲染。
4. SSE 事件格式虽然正确，但事件发送频率太细或太密，前端渲染逻辑在 `done` 前没有逐步刷新。
5. 代理/网关/开发服务器对 SSE 做了压缩、转发缓冲或 fetch 包装，破坏了实时刷新。

## Evidence Plan
- 记录 `ChatService` 每个 chunk 生成时间。
- 记录控制器进入与首次发送时间。
- 如有必要，记录前端收到 chunk 的时间戳。
- 对比“服务端产出时间线”与“浏览器渲染时间线”。

## Instrumentation
- `services/ai4j-factory-service/src/main/java/org/ai4j/factory/chat/ChatService.java`
  - 记录上游订阅、每个 chunk 发出时间、流完成时间。
- `services/ai4j-factory-service/src/main/java/org/ai4j/factory/chat/ChatController.java`
  - 记录请求进入、控制器订阅服务流、每个 SSE 转发时间、响应完成时间。
- `apps/ai4j-factory-ui/src/components/ChatArea.tsx`
  - 记录前端打开 SSE 连接、每个 chunk 到达时间、done 到达时间。

## Current Status
- Instrumentation added.
- Debug server running on `http://127.0.0.1:7777`.
- Log file reset for `runId=pre-fix`.
- Reproduction completed.

## Evidence Analysis
| ID | Hypothesis | Status | Evidence Summary |
|----|------------|--------|------------------|
| A | `ChatService` 先聚合后一次性吐出完整文本 | ⏳ Inconclusive | 本轮实际运行后端没有产生日志，说明运行中的 `8080` 服务尚未带上最新后端埋点，无法直接证明服务端 chunk 时间线。 |
| B | 后端 SSE 被缓冲，导致浏览器直到末尾才收到 | ❌ Rejected | 前端在第二次请求中从首个 chunk 到 `done` 持续收到事件约 5.4 秒，并非末尾一次性抵达。参考日志 `.dbg/trae-debug-log-chat-sse-buffering.ndjson` 第 61、62、948 行。 |
| C | 前端请求方式不是真流式消费 | ❌ Rejected | `ChatArea` 已持续收到大量 `onChunk` 事件，说明 `EventSource` 流式消费正常。参考第 61、62、120、300、930、948 行。 |
| D | 前端渲染链路导致“视觉上突然出现” | ⏳ Inconclusive | 已确认 `onChunk` 在持续触发，但尚未记录 React/DOM paint 时间线，仍需验证是否是 `Markdown` 重渲染或平滑滚动影响了可见更新。 |
| E | Next.js 代理或中间层缓冲 SSE | ❌ Rejected | 聊天页直连 `http://localhost:8080/api/chat/stream/...`，已绕过前端代理；且浏览器持续收到了 chunk。参考第 61 行。 |

## Findings
- 第二次请求打开时间：第 61 行。
- 第二次请求首个 chunk 到达时间：第 62 行，距离打开约 12.4 秒。
- 第二次请求完成时间：第 948 行，距离首个 chunk 约 5.4 秒。
- 说明“网络流式传输”已经成立，当前更可能是展示层的视觉反馈问题，而不是 SSE 根本没流起来。

## Post-Fix Plan
- 将前端每个 chunk 的立即同步渲染改为按 `requestAnimationFrame` 合并刷新，避免主线程被 token 级别更新压满。
- 流式输出期间禁用平滑滚动，避免每次追加文本都触发滚动动画竞争主线程。
- 流式输出期间暂时以纯文本展示 AI 内容，结束后再交给 Markdown 做完整解析与格式化。

## Fix Applied
- `apps/ai4j-factory-ui/src/components/ChatArea.tsx`
  - 用 `requestAnimationFrame` 合并 chunk，移除每 token 一次的同步刷新。
- `apps/ai4j-factory-ui/src/components\MessageList.tsx`
  - 流式期间改为纯文本渲染 AI 消息。
  - 流式期间 `scrollIntoView` 使用 `auto`，结束后恢复 `smooth`。

## Current Status
- Fix applied, instrumentation retained.
- Awaiting post-fix verification with `runId=post-fix`.
