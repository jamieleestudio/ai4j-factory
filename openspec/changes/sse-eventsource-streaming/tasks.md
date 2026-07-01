## 1. 后端 BI Controller 改 GET

- [x] 1.1 `BiController.query` 从 `@PostMapping` 改为 `@GetMapping`，参数从 `@RequestBody QueryRequest` 改为 `@RequestParam`（question、credentialId、modelName、sessionId）
- [x] 1.2 删除 `QueryRequest` record（如果不再被引用）
- [x] 1.3 参数缺失时返回 400（`question`/`credentialId` 必填，`modelName`/`sessionId` 可选）
- [x] 1.4 更新 `BiControllerTest` 用 `MockMvcRequestBuilders.get` 替代 `post`，参数从 body 改为 query param

## 2. 前端统一 SSE 订阅工具

- [x] 2.1 新建 `apps/ai4j-factory-ui/src/utils/sse.ts`，导出 `subscribeSSE(url, callbacks): { close: () => void }`
- [x] 2.2 `subscribeSSE` 内部构造 `new EventSource(url, { withCredentials: true })`，`onmessage` 里 `parseSSEPayload(e.data)` 后 `dispatch`
- [x] 2.3 `onerror` 回调里调 `callbacks.onError?.()` + `callbacks.onDone?.()` + `es.close()`（阻止自动重连）
- [x] 2.4 从 `fetchSSE.ts` 迁移 `parseSSEPayload`、`parseSSELine`、`SseEvent` 类型、`SSECallbacks` 接口、`dispatch` 函数到 `sse.ts`
- [x] 2.5 删除 `fetchSSE.ts`（包括 `fetchSSE` 导出）

## 3. 前端 BiArea 适配

- [x] 3.1 `BiArea.tsx` 把 `import { fetchSSE } from "../utils/fetchSSE"` 改为 `import { subscribeSSE } from "../utils/sse"`
- [x] 3.2 `handleQuery` 从 `await fetchSSE(url, body, callbacks)` 改为构造 GET URL（`encodeURIComponent` 拼接 question/credentialId/modelName/sessionId）+ `const sub = subscribeSSE(url, callbacks)`
- [x] 3.3 把 `sub` 存入 ref，在 `onDone` / `onError` 回调里调 `sub.close()`
- [x] 3.4 组件卸载时调 `sub.close()`（`useEffect` cleanup）
- [x] 3.5 删除 `await new Promise((r) => setTimeout(r, 0))` 相关代码（`sse.ts` 不需要，EventSource 天然按帧触发）

## 4. 前端 ChatArea 适配

- [x] 4.1 `ChatArea.tsx` 删除手搓 `new EventSource` 代码（line 66-117）
- [x] 4.2 改用 `subscribeSSE(url, { onChunk, onDone, onError })`
- [x] 4.3 `eventSourceRef` 改为 `subscriptionRef`，类型从 `EventSource | null` 改为 `{ close: () => void } | null`
- [x] 4.4 组件卸载时调 `subscriptionRef.current?.close()`

## 5. 测试

- [x] 5.1 新建 `sse.test.ts`，mock `EventSource` 构造函数，覆盖：onmessage 解析 6 种事件、onerror 触发 onError + onDone + close、显式 close 不触发回调
- [x] 5.2 删除 `fetchSSE.test.ts`
- [x] 5.3 更新 `BiArea.test.tsx`：mock `subscribeSSE`，验证 URL 拼接正确（question 正确 URL encode）
- [x] 5.4 后端运行 `BiControllerTest`，确认 GET 路径测试通过

## 6. 端到端验证

- [x] 6.1 启动后端 `mvn spring-boot:run`，前端 `pnpm dev`
- [ ] 6.2 浏览器 DevTools Network → `bi/query` 请求 → EventStream 标签，确认每个事件时间戳间隔 50-100ms（不再攒批）
- [ ] 6.3 BI 查询 "看看区域和产品线的销售情况"，肉眼确认洞察文本逐字流式出现
- [ ] 6.4 Chat 对话 "请用一段话介绍春天"，肉眼确认逐字流式。若仍"一起来"，记录症状并单独开 change 排查 React 渲染层
- [x] 6.5 后端 `curl -N -G "http://localhost:8080/api/bi/query" --data-urlencode "question=..." ...` 确认 GET 端点流式正常
