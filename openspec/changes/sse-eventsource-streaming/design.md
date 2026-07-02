## Context

`unified-sse-protocol` 把 SSE 事件契约从字符串前缀升级为 JSON envelope，但留下了一个未明文约束的维度——**传输方式**。结果两端各自选了不同路径：

- **Chat 端** (`ChatArea.tsx`): 浏览器原生 `EventSource` + GET。`EventSource` 内部按 SSE 帧边界解析，每个帧触发一次 `onmessage` 回调。
- **BI 端** (`BiArea.tsx` → `fetchSSE.ts`): `fetch` POST + `response.body.getReader()` + 手动 `split("\n")` + `await setTimeout(0)`。

后端 curl 验证证明两端都是真·流式（每 50-100ms 一个 chunk），但 BI 端在前端表现"一起来"。根因在 `getReader()` 这一层。

## 验证证据

### 后端真流式（curl -N 时间戳）

```
[17:59:33.932] status:analyzing
[17:59:34.098] intent          (+166ms)
[17:59:34.284] status:querying  (+186ms)
[17:59:34.444] status:insight   (+160ms)
[17:59:36.712] chunk "根据"      (+2268ms — LLM 首 token 延迟)
[17:59:36.853] chunk "查询"      (+141ms)
[17:59:36.969] chunk "结果"      (+116ms)
... 每 50-100ms 一个 chunk，持续 ~30 秒，共 ~250 个事件
```

### 前端 `fetch + getReader` 攒批（Node 复现）

```
[18:01:45.636] +1163ms read() => 437B raw   ← 后端 1 秒内发的 4 个事件被攒一坨
[18:01:50.439] +5966ms read() => 42B raw    ← 4.8 秒黑洞
[18:01:50.439] +5966ms read() => 84B raw    ← 紧接着又一坨
[18:01:50.440] +5967ms read() => 87B raw
[18:01:50.441] +5968ms read() => 860B raw   ← 单次 read 8KB 含 ~30 个事件
[18:01:50.442] +5969ms read() => 2114B raw
```

3 毫秒内分发了 26 个事件，全部来自同一批 `read()` 返回的字节。

### 浏览器实测

用户在浏览器 DevTools EventStream 标签确认："time 大部分是一样的"——证明浏览器 `fetch + getReader` 也有同样的攒批行为，不是 Node 独有。

## Goals / Non-Goals

**Goals:**
- 消除 BI 端 SSE 攒批，让前端真正逐 token 渲染
- 统一 BI 和 Chat 的 SSE 消费代码（两份手搓实现合并）
- 把"必须用 EventSource"写进 spec，防止未来回退到 `fetch + getReader`

**Non-Goals:**
- 不改事件 envelope 契约（`status`/`intent`/`chunk`/`result`/`error`/`done` 不变）
- 不改后端 BI 执行管线（意图提取 → SQL → 查询 → 洞察的步骤不变）
- 不改 Chat 的功能集
- 不引入 SSE 协议层的高级特性（`event:` 字段、`retry:`、`id:` 等）
- 不做 URL 长度校验（依赖浏览器限制）

## Decisions

### Decision 1: BI 端点 POST → GET

`EventSource` 规范只支持 GET，不支持 POST 也不能自定义 header。要让 BI 用 `EventSource`，必须把端点改成 GET，参数全进 query string。

```
旧: POST /api/bi/query  body={"question":"...","credentialId":1,...}
新: GET /api/bi/query?question=...&credentialId=1&modelName=...&sessionId=...
```

**URL 长度风险评估**：

| 字段 | 典型长度 | 极端长度 |
|------|---------|---------|
| question（中文） | 10-50 字 | <500 字 |
| credentialId | 1-2 字 | 10 字 |
| modelName | 10-20 字 | 50 字 |
| sessionId (UUID) | 36 字 | 36 字 |
| URL encode 膨胀 | 中文 ×3 | - |
| **合计** | **<200 字** | **<2KB** |

浏览器 URL 长度限制：Chrome/Edge/Firefox 都 ≥2MB。撞到限制的概率几乎为零。**不设上限，不做校验。**

**Alternatives considered:**
- *两步法（POST 创建 session 返回 id，GET /stream/{id} 取流）*：保留 POST body 能力，但要管理 session 生命周期、清理过期 session、跨实例共享 session（如果将来水平扩展）。复杂度不值。
- *保留 POST + 用 `fetch` 但改用 `pipeThrough(TextDecoderStream)`*：仍受底层 read 攒批影响，不解决根因。

### Decision 2: 前端用 `EventSource`，封装统一 `subscribeSSE` 工具

```ts
// utils/sse.ts (新文件，替代 fetchSSE.ts)
export type SSESubscription = {
  close: () => void;
};

export function subscribeSSE(
  url: string,
  callbacks: SSECallbacks
): SSESubscription {
  const es = new EventSource(url, { withCredentials: true });

  es.onmessage = (e) => {
    const event = parseSSEPayload(e.data);
    if (!event) return;
    dispatch(event, callbacks);
  };

  es.onerror = () => {
    // EventSource 默认会自动重连，但流式场景下重连无意义
    // done 事件由后端显式推送，正常完成时 onmessage 收到 done 后 close
    // 这里只在异常中断时通知前端
    callbacks.onError?.("SSE connection error");
    callbacks.onDone?.();
    es.close();
  };

  return { close: () => es.close() };
}
```

`done` 事件由后端显式推送（已有契约），前端在 `onDone` 回调里调 `close`，避免 EventSource 自动重连触发 `onerror` 后又重试。

### Decision 3: 保留 `fetchSSE.ts` 的纯函数部分

`parseSSEPayload` / `parseSSELine` / 类型定义（`SseEvent` discriminated union）保留，移到 `utils/sse.ts`。只删掉 `fetchSSE` 这个基于 `fetch + getReader` 的导出。

测试也要改：`fetchSSE.test.ts` → `sse.test.ts`，mock `EventSource` 构造函数。

### Decision 4: `EventSource` 不支持自定义 header

CORS 场景下不能携带 `Authorization` header。当前 ChatArea 已经用 `withCredentials: true`（cookie 模式），BI 也照搬。后端 `WebConfig` 已配置 `allowCredentials(true)`，无需改动。

如果未来要支持 token-based auth，需要走 cookie 或 query string 传 token。当前无需考虑。

### Decision 5: Chat 端一并迁移到 `subscribeSSE`

ChatArea 现在是手搓 `new EventSource`，逻辑分散。改用 `subscribeSSE` 后：

```ts
// 旧
const es = new EventSource(url, { withCredentials: true });
es.onmessage = (e) => { ... };
es.onerror = () => { ... };
eventSourceRef.current = es;

// 新
const sub = subscribeSSE(url, {
  onChunk: (content) => setMessages(prev => ...),
  onDone: () => { setIsLoading(false); sub.close(); },
  onError: (msg) => { ...; sub.close(); },
});
subscriptionRef.current = sub;
```

ChatArea 的"一起来"如果真的是 React batching 导致，需要单独排查 React 渲染层（可能需要 `flushSync` 或 `useTransition`）。但先用统一 SSE 工具消除变量，再观察 Chat 症状是否消失。

## Risks / Trade-offs

- **[Breaking change 无版本协商]** → 内部项目，前后端同仓库同步部署。改完一次性发，不做灰度。与 `unified-sse-protocol` 的 breaking change 合并发布，避免两次升级
- **[GET 暴露 question 在 URL]** → question 进 access log、浏览器 history、Referer header。当前是内部 BI 工具，无敏感数据。若未来有合规要求，再补 POST 两步法
- **[EventSource 自动重连]** → 后端推送 `done` 后前端必须主动 `close()`，否则 EventSource 默认会重连。`subscribeSSE` 在 `onDone` 回调里自动 `close`，调用方无需关心
- **[Chat 一起来症状可能不消失]** → 如果 React 19 automatic batching 是根因，本 change 不解决。但先用统一 SSE 工具消除代码变量，再单独排查渲染层。tasks 中保留一个验证任务，若 Chat 仍有症状则单独开 change
- **[React 19 automatic batching 实测确认]** → 端到端验证发现：EventStream 标签显示 chunk 事件分散到达（50-100ms 间隔），但用户画面仍是"文本空白几秒后突然整段出现"。根因是 React 19 的 automatic batching 把多次 `setMessages` 调用合并成一次重渲染。修复：在 `onChunk` 回调里用 `flushSync` 包裹 `setMessages`，强制每次 chunk 立即提交渲染。这是 SSE 传输层之外的 React 渲染层问题，但症状同一，未拆 change
- **[CORS 预检]** → GET 请求不触发 preflight（简单请求），比 POST 更轻量。`EventSource` 不支持自定义 header，CORS 策略靠 cookie 传递凭证
