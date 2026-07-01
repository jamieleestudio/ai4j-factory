## Why

`unified-sse-protocol` 定义了 JSON envelope 事件契约，但未约束传输方式。BI 端实际走 `fetch + getReader()` + 手动按行解析，在浏览器和 Node 环境下都会把后端真流式的事件**攒批**：一次 `read()` 返回 8KB 字节、含几十个 SSE 帧，所有帧在同步循环里被一次性 dispatch，即便每个 dispatch 之间有 `await setTimeout(0)` 也救不回 read 内部的批。

实测证据（2026-07-01）：

- 后端 `curl -N` 验证：`status` / `intent` / `chunk` 事件按 50-100ms 间隔逐个到达，每个 chunk 都是 1-5 字的小片段，真·流式
- 前端复现 `fetchSSE` 的 `fetch + getReader`：第一次 `read()` 在 +1163ms 返回 437B 含 4 个事件；之后 4.8 秒黑洞；再一次性返回 860B / 2114B 的多个 chunk；26 个事件挤在 3ms 内 dispatch 完
- `unified-sse-protocol` 的 tasks 7.5/7.6（浏览器端 BI / Chat 流式验证）从未勾选，端到端流式行为从未被实际验证过

Chat 端用浏览器原生 `EventSource`，理论上按帧边界触发 `onmessage`，但用户报告 Chat 也有"一起来"症状——可能是 React 19 automatic batching 把多次同步 `setMessages` 合并，需要一并排查并纳入本次改造。

根因不在事件契约，在传输层。本 change 不动 envelope，只改传输方式。

## What Changes

- **BI 端点从 POST 改为 GET** **BREAKING**：`/api/bi/query` 从 `@PostMapping` 改为 `@GetMapping`，参数 `question` / `credentialId` / `modelName` / `sessionId` 全部进 query string。URL 长度依赖浏览器默认限制（~2MB），不做长度校验
- **前端 BI 改用浏览器原生 `EventSource`**：`fetchSSE.ts` 重写为基于 `EventSource` 的实现，按 SSE 帧边界触发回调；删除 `fetch + getReader + 手动 split("\n")` 路径
- **前端 Chat 适配新 `EventSource` 工具**：`ChatArea.tsx` 从手搓 `new EventSource` 改为调用统一的 `subscribeSSE` 工具，确保两个端点的 SSE 处理逻辑共用一份代码
- **`bi-streaming` spec 修改**：现有 spec 明文要求 `fetch + ReadableStream`、禁止 `EventSource`，需翻转为必须用 `EventSource`
- **`sse-event-protocol` spec 新增传输方式要求**：明确 BI/Chat 端点必须用 GET，前端必须用 `EventSource`

## Capabilities

### Modified Capabilities
- `bi-streaming`: 端点从 POST 改 GET；前端从 `fetch + getReader` 改为 `EventSource`
- `chat-streaming`: 前端从手搓 `EventSource` 改为统一 `subscribeSSE` 工具，消除两份 SSE 处理代码
- `sse-event-protocol`: 新增传输方式约束（GET + EventSource）

## Impact

- **后端**：`BiController.java`（POST → GET，参数从 `@RequestBody QueryRequest` 改为 `@RequestParam`）
- **前端**：`fetchSSE.ts` 重写（导出 `subscribeSSE` 基于 `EventSource`，保留 `parseSSEPayload` 等纯函数）；`BiArea.tsx`（调用方式从 `await fetchSSE(...)` 改为 `subscribeSSE(...)` 返回 cleanup 函数）；`ChatArea.tsx`（替换手搓 `EventSource` 调用）
- **测试**：`fetchSSE.test.ts` 重写为 `subscribeSSE.test.ts`（mock `EventSource`）；`BiControllerTest.java` 调整请求方式
- **API 变更** **BREAKING**：`POST /api/bi/query` → `GET /api/bi/query?question=...&credentialId=...&modelName=...&sessionId=...`。前端旧版本不兼容
- **依赖**：无需新增；浏览器原生 `EventSource` 已支持
- **与 `unified-sse-protocol` 的关系**：本 change 假定 envelope 协议先落地（unified-sse-protocol archive 后再做），或同步落地。如果 unified-sse-protocol 未 archive，本 change 的 spec 修改会与之冲突，需先 archive 之
