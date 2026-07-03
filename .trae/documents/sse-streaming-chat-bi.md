# SSE 无法流式刷出（Chat + BI）排查与修复计划

## Summary

目标：解决前端界面上 Chat 与 BI 的 SSE 都表现为“长时间等待后一次性输出”，实现真正的增量展示（逐段/逐 token 刷新）。

结论导向的推荐路线：在保持 `spring-boot-starter-web`（Spring MVC + Tomcat）的前提下，把当前 `ResponseEntity<Flux<ServerSentEvent<String>>>` 的写出方式改为 `SseEmitter` 主动 `send()`，以强制每次事件写入后立即 flush；同时加上首包事件/心跳/分片聚合来降低代理与容器缓冲的概率。若后续确认必须强依赖响应式管线（或想减少手写桥接），再评估迁移到 `spring-boot-starter-webflux`。

## Current State Analysis（基于仓库实况）

### 后端接口形态

- Chat SSE：`GET /api/chat/stream/{credentialId}`
  - 实现：[ChatController.java](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/services/ai4j-factory-service/src/main/java/org/ai4j/factory/chat/ChatController.java#L23-L34)
  - 返回：`ResponseEntity<Flux<ServerSentEvent<String>>>`
- BI SSE：`GET /api/bi/query`
  - 实现：[BiController.java](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/services/ai4j-factory-service/src/main/java/org/ai4j/factory/bi/BiController.java#L67-L170)
  - 返回：`ResponseEntity<Flux<ServerSentEvent<String>>>`

两者都设置了典型 SSE 头（`text/event-stream`、`Cache-Control no-store`、`X-Accel-Buffering: no`、`keep-alive`），但用户实际观察到仍然“最后一起出来”。

### 流式内容来源

- Chat：直接把 `chatClient.prompt().user(message).stream().content()` 的上游流映射为 `ChunkEvent` 并输出
  - [ChatService.java](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/services/ai4j-factory-service/src/main/java/org/ai4j/factory/chat/ChatService.java#L20-L28)
- BI：洞察生成同样来自 `chatClient.prompt().user(prompt).stream().content()`，但在 Controller 中做了“累积全文 -> 只吐 delta”的处理，再追加 `ResultEvent`/`DoneEvent`
  - [InsightGenerationService.java](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/services/ai4j-factory-service/src/main/java/org/ai4j/factory/bi/insight/InsightGenerationService.java#L36-L45)
  - [BiController.java](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/services/ai4j-factory-service/src/main/java/org/ai4j/factory/bi/BiController.java#L123-L150)

### 前端消费方式（确认非一次性读取）

前端使用 `EventSource` 的 `onmessage` 逐条事件消费，并在 `chunk` 到来时立即渲染：

- Chat：显式直连后端（避免 Next 代理缓冲），并在 onChunk 内 `flushSync` 更新 UI
  - [ChatArea.tsx](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/apps/ai4j-factory-ui/src/components/ChatArea.tsx#L64-L107)
- BI：同样直连后端，onChunk 逐步累加
  - [BiArea.tsx](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/apps/ai4j-factory-ui/src/components/BiArea.tsx#L192-L293)
- SSE 工具：`new EventSource(url, { withCredentials: true })`
  - [sse.ts](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/apps/ai4j-factory-ui/src/utils/sse.ts#L119-L144)

因此，“一次性出来”更像是 **服务端写出/flush** 或 **中间代理缓冲** 问题，而不是前端读法问题。

### 关键约束/风险点

- 后端依赖为 `spring-boot-starter-web`（MVC），未引入 `spring-boot-starter-webflux`
  - [ai4j-factory-service/pom.xml](file:///c:/Users/lixiaofeng/Repos/ai4j-factory/services/ai4j-factory-service/pom.xml#L17-L26)
- 在 Spring MVC 场景下，`Flux` 返回值的写出行为受容器/MessageConverter/异步写出策略影响，实践上更容易出现“缓冲到一定量才 flush”的现象。
- 即使没有自定义 Filter，**反向代理/Nginx/Ingress/网关** 默认也常常对 `text/event-stream` 做缓冲或压缩，需要显式配置。

## Proposed Changes（推荐实现路线：MVC + SseEmitter 强制 flush）

### 1) 增加一个“Flux -> SseEmitter”的桥接工具（核心）

新增一个类（建议放在 `org.ai4j.factory.sse` 包）：

- 目标：把 `Flux<ServerSentEvent<String>>` 订阅后逐条 `emitter.send(...)`，每条事件都会触发 Servlet Response flush，从而让浏览器实时收到。
- 需要能力：
  - 客户端断开/超时：dispose subscription
  - 发送异常（如 `IOException`）：completeWithError 并 dispose
  - 可选：插入首包 event / 心跳 / chunk 聚合

建议新增文件：

- `services/ai4j-factory-service/src/main/java/org/ai4j/factory/sse/SseEmitterBridge.java`

桥接策略（实现细节会在执行阶段落地）：

- `new SseEmitter(0L)`（不超时）
- `Disposable d = flux.subscribe(onNext, onError, onComplete)`
- `emitter.onCompletion/onTimeout/onError` 时 `d.dispose()`
- `onNext(ServerSentEvent<String> e)`：
  - 如果 `e.data() != null`：`emitter.send(SseEmitter.event().data(e.data()))`
  - 否则（心跳/comment 场景）：`emitter.send(SseEmitter.event().comment("keepalive"))`

### 2) ChatController 改为返回 `ResponseEntity<SseEmitter>`

修改文件：

- `services/ai4j-factory-service/src/main/java/org/ai4j/factory/chat/ChatController.java`

修改点：

- 返回类型从 `ResponseEntity<Flux<ServerSentEvent<String>>>` 改为 `ResponseEntity<SseEmitter>`
- `Flux<ServerSentEvent<String>> flux = chatService.streamChat(...)`
- 在 Controller 层为 flux 添加：
  - 首包：`StatusEvent("connected", "...")` 或 comment（用于尽早 flush headers）
  - 心跳：每 10–15 秒一个 comment（降低中间层 idle timeout + 促进持续 flush）
  - 可选：对 token 进行 `bufferTimeout` 聚合，避免每个字符都发一次导致极小包更容易被缓冲
- `SseEmitter emitter = SseEmitterBridge.from(flux)`
- 继续保留现有 SSE 相关响应头（Cache-Control、X-Accel-Buffering、keep-alive）

### 3) BiController 改为返回 `ResponseEntity<SseEmitter>`

修改文件：

- `services/ai4j-factory-service/src/main/java/org/ai4j/factory/bi/BiController.java`

修改点：

- 类似 ChatController，把 `body(body)` 改为 `body(emitter)`
- 现有 `Flux.concat(...)` 的业务拼装逻辑尽量不动，仅在最终写出层改为 emitter，降低风险
- BI 已有首包 `StatusEvent`，仍建议补一个心跳（BI 链路更长、更容易被 proxy 断开/缓冲）

### 4) （可选）显式关闭压缩 + 增强可观测性

修改文件（可选，按实际排查结果决定）：

- `services/ai4j-factory-service/src/main/resources/application.yml`

候选配置：

- `server.compression.enabled: false`（避免 gzip 造成缓冲）
- 增加非常轻量的日志（不打印 token 内容，只打印事件计数/时间戳），用于确认“后端确实在持续 emit”：
  - 在桥接工具里按固定间隔统计（例如每 N 条/每秒打印一次）

### 5) 反向代理/网关自检与配置建议（不改代码也要做）

如果链路前面有 Nginx/Ingress/API Gateway，按以下方向排查（执行阶段给出最终建议清单）：

- 关闭代理缓冲：`proxy_buffering off;` 或对应 Ingress annotation
- 关闭 gzip/压缩：对 `text/event-stream` 禁用
- 确保 chunked 传输不被合并
- 增大/禁用上游 response buffer
- 确认 `X-Accel-Buffering: no` 在该代理上生效（仅 Nginx 识别）

## Assumptions & Decisions

- 现象：Chat 与 BI 都在 UI 上“最后一起出来”；前端使用 EventSource，已排除“前端一次性读取”的主因。
- 决策：优先采用 **Spring MVC + SseEmitter 主动 send** 的方式修复 flush，不先引入 WebFlux（改动面更大）。
- 兼容性：保持现有 SSE 协议（`data` 为 JSON 字符串，event.type 为 chunk/status/done...），前端无需改动。

## Verification（执行阶段的验收与自检步骤）

### A. 直连后端验证（排除代理）

- 用 curl 直连后端 SSE（必须用 `-N` 禁用 curl 缓冲）：
  - `curl -N "http://localhost:8080/api/chat/stream/{credentialId}?message=hello&model=...&sessionId=..."`
  - `curl -N "http://localhost:8080/api/bi/query?question=...&credentialId=...&modelName=...&sessionId=..."`
- 预期：能看到连续多段 `data: {...}\n\n` 逐步输出，而不是结束时一次性打印。

### B. 浏览器网络面板验证

- Network -> SSE 请求 -> Response/Preview：
  - 预期响应内容持续增长，且前端 UI 按 chunk 实时更新。

### C. 代理链路回归（如存在）

- 在“直连后端”已 OK 的情况下，再通过实际部署链路访问：
  - 若再次退化为“一起出来”，说明问题在代理层，需要按本计划第 5 点配置处理。

### D. 回归用例

- Chat：发送一条较长的问题，验证 UI 逐步出现文字
- BI：触发“analyzing/querying/insight”阶段事件与 chunk 逐段出现

