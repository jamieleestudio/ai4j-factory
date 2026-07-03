# BI Agent 规划方案

## Summary

* 目标是在 `services/agent` 下新建独立模块 `ai4j-agent-bi-service`，以干净的 Agent 架构逐步替代当前 `services/ai4j-factory-service` 中的 BI 能力。

* 技术路线以 `Spring AI` + `Spring AI Alibaba 2.x` 为核心，首期按完整链路规划：语义层定义 -> 意图理解 -> 受控 SQL 规划 -> JDBC 查询 -> 洞察生成 -> SSE 打字机式流式输出。

* 首期模型配置采用 `application.yml + 环境变量`，不复用旧的 DB 凭证中心；但在架构上保留 `provider adapter` 扩展点，优雅支持 DashScope 与 OpenAI 兼容模型。

* 新协议允许升级，不沿用旧 BI 的 API/SSE 契约；但会保留前端易接入的流式特性和结构化结果返回。

## Current State Analysis

### 1. 仓库与模块现状

* 根聚合 `pom.xml` 当前包含 `services/ai4j-factory-service`、`services/mcp`、`services/agent` 三个模块。

* `services/agent/pom.xml` 当前只有一个子模块 `ai4j-agent-assistbot`。

* `services/agent/ai4j-agent-assistbot` 目前是演示性质项目，已有文件非常少：

  * `src/main/java/org/ai4j/agent/assistbot/AssistBotApplication.java`

  * `controller/AgentController.java`

  * `controller/AgentDemoController.java`

  * `config/AgentToolsConfig.java`

  * `tool/FlightBookingService.java`

  * `src/main/resources/application.yml`

* `assistbot` 已经接入 `spring-ai-alibaba-starter-dashscope`，但只覆盖最基础的 `ChatClient` 与单个 demo tool，没有形成可扩展的 BI 架构。

### 2. 旧 BI 服务的主要问题

* `services/ai4j-factory-service` 把 `chat`、`bi`、`shared credential`、`sse` 全部放在一个模块内，边界已经混杂。

* `org.ai4j.factory.bi.BiQueryWorkflowService` 当前同时承担会话恢复、意图提取、澄清分支、SQL 构建阶段事件、查询执行阶段事件、洞察事件拼装等职责，违反单一职责。

* `org.ai4j.factory.chat.ChatClientFactory` 强绑定旧的 OpenAI SDK 与 `ModelCredentialRepository`，不适合作为新 Agent 的模型抽象层。

* 旧 BI 入口 `org.ai4j.factory.bi.BiController` 使用 `GET /api/bi/query`，这与完整查询对象、升级协议和后续扩展都不够匹配。

* 现有测试如 `BiControllerTest` 直接围绕工作流细节与事件顺序编写，说明当前架构耦合较高，重构成本大。

### 3. 依赖与版本现状

* 根 `pom.xml` 当前为 `Spring Boot 4.1.0`、`Java 21`、`spring-ai-bom 2.0.0-M8`。

* 根 `pom.xml` 中 `spring-ai-alibaba.version` 仍是 `1.1.2.1`，与本次“按 Spring AI Alibaba 2 实现”的目标不一致。

* `ai4j-agent-assistbot/pom.xml` 当前依赖 `spring-ai-alibaba-starter-dashscope`，可作为新模块的最小接入参考，但不能直接扩展成 BI 生产架构。

### 4. 可直接复用的经验，不直接复用的实现

* 必须保留的经验：

  * SSE 流式输出是硬约束，且需要真实增量输出，避免缓冲后一次性返回。

  * BI 领域仍然需要语义层，且语义最终会转为 SQL。

* 明确不复用的旧设计（严禁参照）：

  * 严禁在开发新模块时参照或复制旧版 `ai4j-factory-service` 的 BI 实现代码。

  * 不复用 `ai4j-factory-service` 的包结构。

  * 不复用旧的 `ChatClientFactory`、`BiQueryWorkflowService`、旧 SSE 事件模型作为新实现基础。

  * 不把旧服务的“一个工作流类串所有阶段”模式带入新项目。

## Proposed Changes

### 1. 模块与构建层调整

#### `pom.xml`

* 变更内容：

  * 将 `spring-ai-alibaba.version` 升级到 `2.x` 版本线。

  * 保持 `Spring AI BOM 2.x` 与 `Spring AI Alibaba 2.x` 对齐。

* 原因：

  * 当前根版本仍停留在 `1.1.2.1`，与本次技术路线不一致。

* 实施方式：

  * 先锁定 `Spring AI Alibaba 2.x` 可用版本，再统一更新根属性，避免新模块单独漂移版本。

  * 实施阶段优先以 Maven Central 已可用的 `2.0.0-M1.x` 版本线验证兼容性；若存在与当前 `Spring Boot 4.1.0` 的实际兼容问题，再同步调整 BOM 组合。

#### `services/agent/pom.xml`

* 变更内容：

  * 新增子模块 `ai4j-agent-bi-service`。

* 原因：

  * 保持 `services/agent` 作为 Agent 家族聚合模块。

* 实施方式：

  * 保留现有 `ai4j-agent-assistbot` 作为 demo/sandbox，不与 `ai4j-agent-bi-service` 混用。

#### `services/agent/ai4j-agent-bi-service/pom.xml`

* 新建内容：

  * 独立的 Spring Boot 可运行模块。

* 依赖规划：

  * `spring-boot-starter-webflux`

  * `spring-boot-starter-validation`

  * `spring-boot-starter-jdbc`

  * `spring-boot-starter-actuator`

  * `spring-ai-alibaba-starter-dashscope`

  * `spring-ai-starter-model-openai`

  * `spring-boot-starter-test`

  * `reactor-test`

* 原因：

  * WebFlux 适合打字机式 SSE。

  * JDBC 负责受控 SQL 查询。

  * OpenAI starter 仅作为“兼容模型适配器”的基础设施，不作为主导架构。

### 2. 新模块目录与分层边界

#### 新模块路径

* `services/agent/ai4j-agent-bi-service/src/main/java/org/ai4j/agent/bi`

#### 包结构决策

```text
org.ai4j.agent.bi
├── BiAgentApplication.java
├── api
│   ├── http
│   │   ├── BiQueryController.java
│   │   ├── dto
│   │   │   ├── BiQueryRequest.java
│   │   │   ├── BiSseEvent.java
│   │   │   ├── ResultPayload.java
│   │   │   └── ErrorPayload.java
│   └── advice
│       └── GlobalExceptionHandler.java
├── application
│   ├── orchestrator
│   │   └── BiAgentOrchestrator.java
│   ├── service
│   │   ├── IntentService.java
│   │   ├── SqlPlanningService.java
│   │   ├── QueryExecutionService.java
│   │   ├── InsightService.java
│   │   └── StreamingResponseService.java
│   └── port
│       ├── ModelGateway.java
│       ├── SemanticCatalogPort.java
│       ├── SqlPlannerPort.java
│       ├── DataQueryPort.java
│       └── PromptRepositoryPort.java
├── domain
│   ├── semantic
│   │   ├── SemanticCatalog.java
│   │   ├── SubjectDefinition.java
│   │   ├── MetricDefinition.java
│   │   └── DimensionDefinition.java
│   ├── planning
│   │   ├── QueryIntent.java
│   │   ├── QueryPlan.java
│   │   ├── SqlStatement.java
│   │   └── QueryFilter.java
│   └── result
│       ├── InsightAnswer.java
│       └── QueryResultTable.java
├── infrastructure
│   ├── config
│   │   ├── BiAgentProperties.java
│   │   ├── ProviderProperties.java
│   │   └── BiAgentConfiguration.java
│   ├── model
│   │   ├── ModelGatewayRegistry.java
│   │   ├── dashscope
│   │   │   └── DashScopeModelGateway.java
│   │   └── openai
│   │       └── OpenAiCompatibleModelGateway.java
│   ├── prompt
│   │   └── ClasspathPromptRepository.java
│   ├── semantic
│   │   └── YamlSemanticCatalogLoader.java
│   ├── sql
│   │   └── SemanticSqlPlanner.java
│   ├── data
│   │   └── JdbcWarehouseQueryAdapter.java
│   └── sse
│       └── SseEventEncoder.java
└── support
    └── observability
        └── BiAgentObservation.java
```

#### 分层原则

* `api` 只负责 HTTP/SSE 协议，不允许包含语义解析与 SQL 逻辑。

* `application` 只编排用例，不感知具体模型厂商与 JDBC 实现。

* `domain` 只放 BI 语义与查询模型，不依赖 Spring。

* `infrastructure` 承载 Spring AI Alibaba、OpenAI-compatible、JDBC、classpath 配置等技术细节。

### 3. 模型接入架构

#### 目标

* 以 `Spring AI Alibaba 2.x` 为主线路。

* 同时优雅支持兼容模型，不重走旧 `ChatClientFactory + Repository + SDK 拼装` 的路径。

#### 设计决策

* 由 `application.port.ModelGateway` 暴露统一能力：

  * `generateStructuredIntent(...)`

  * `streamInsight(...)`

* 由 `infrastructure.model.ModelGatewayRegistry` 根据配置选择具体适配器。

* 首期提供两类适配器：

  * `DashScopeModelGateway`

  * `OpenAiCompatibleModelGateway`

#### 配置来源

* V1 只使用配置文件与环境变量：

  * `application.yml` 中配置 provider、默认模型、超时、streaming 开关。

  * 密钥与 endpoint 通过环境变量注入。

* 不引入 DB 凭证中心，不迁移旧 `shared/credential/*`。

#### 原因

* 先把 Agent 内核和边界做干净，避免在 V1 再把“配置中心 + 模型工厂 + 业务工作流”揉在一起。

* 未来若需要 UI 管理 provider，只需要在 `ModelGatewayRegistry` 前增加一个新的配置源适配器，不破坏应用层端口。

### 4. 语义层与 SQL 规划

#### 语义层存储

* 新模块使用 `src/main/resources/semantic/*.yaml` 存放语义目录。

* 首期定义一个 `SemanticCatalog` 聚合根，内部包含：

  * `SubjectDefinition`

  * `MetricDefinition`

  * `DimensionDefinition`

  * 允许的 filter operator

  * 默认 limit 与排序策略

#### 设计原则

* 模型不能直接输出 SQL。

* 模型只输出结构化意图 `QueryIntent`。

* SQL 由 `SemanticSqlPlanner` 结合语义目录在系统内生成。

#### SQL 规划职责

* `IntentService`

  * 输入：用户问题 + 语义摘要

  * 输出：`QueryIntent`

* `SqlPlanningService`

  * 输入：`QueryIntent`

  * 输出：`QueryPlan`

* `SemanticSqlPlanner`

  * 输入：`QueryIntent + SemanticCatalog`

  * 输出：`SqlStatement(sql, bindings, selectedDimensions, selectedMetrics)`

#### 必须支持的约束

* 只允许命中语义层已声明的 subject / metric / dimension。

* 只允许白名单聚合函数与白名单过滤运算符。

* 默认限制结果集大小。

* 所有变量值必须参数化绑定，禁止字符串拼接注入。

* 当语义解析不足时，优先返回澄清/错误，不降级为模型自由写 SQL。

### 5. Agent 编排与职责拆分

#### `application.orchestrator.BiAgentOrchestrator`

* 职责：

  * 串联“意图 -> 规划 -> 查询 -> 洞察 -> SSE 输出”五个阶段。

  * 仅负责流程，不直接持有 ChatClient、JDBC Template、配置对象。

#### 五阶段编排

1. `validate request`
2. `extract intent`
3. `plan sql`
4. `execute query`
5. `stream insight and final result`

#### 输出事件策略

* 编排器只产出领域级事件流，不直接构造 HTTP 响应。

* HTTP 层再把事件编码为 SSE。

#### 原因

* 这样可以分别测试：

  * 编排顺序

  * 事件时序

  * SQL 正确性

  * 模型适配器行为

* 避免再次出现旧 `BiQueryWorkflowService` 那种“既编排又执行业务又管协议事件”的大类。

### 6. SSE 协议与打字机效果

#### API 决策

* 新接口使用 `POST /api/bi/query`

* `Content-Type: application/json`

* 响应 `Content-Type: text/event-stream`

#### 请求 DTO

```json
{
  "question": "按区域统计最近30天销售额",
  "conversationId": "optional",
  "provider": "dashscope",
  "model": "qwen-plus",
  "options": {
    "trace": false
  }
}
```

#### SSE 事件集合

* `status`

  * 阶段提示，例如 `analyzing`、`planning`、`querying`、`answering`

* `chunk`

  * 洞察文本的增量片段，用于打字机效果

* `result`

  * 最终结构化结果，包含 `summary`、`sqlPreview`、`columns`、`rows`、`chartType`

* `error`

  * 业务或基础设施错误

* `done`

  * 结束标记

#### 不对外暴露的事件

* 调试级 trace 不默认放入公开协议。

* 如后续确需调试模式，使用 `options.trace=true` 额外开启，但不污染正常事件流。

#### 打字机效果实现要求

* 由 `InsightService` 返回 `Flux<String>`。

* `StreamingResponseService` 将 token/chunk 转为逐段 SSE。

* 禁止先聚合完整文本再发送。

* 对 JDBC 查询等阻塞调用使用专用调度切换，避免阻塞 WebFlux 事件循环。

### 7. 基础设施与配置文件

#### `src/main/resources/application.yml`

* 新增配置块：

  * `server.port`

  * `spring.datasource.*`

  * `bi-agent.streaming.*`

  * `bi-agent.providers.*`

  * `bi-agent.semantic.location`

  * `bi-agent.prompt.location`

#### `src/main/resources/prompts/`

* 新增模板文件：

  * `intent-system.st`

  * `intent-user.st`

  * `insight-system.st`

  * `insight-user.st`

#### `src/main/resources/semantic/`

* 新增样例语义文件：

  * `sales.yaml`

#### 原因

* Prompt 与 semantic catalog 外置，有利于维护、审阅和后续演进。

* 避免把大段提示词与 JSON schema 继续塞进 Java 代码。

### 8. 前端规划

#### 当前前端现状

* 当前前端只有一个 Next.js app：`apps/ai4j-factory-ui`。

* 当前首页 `src/app/page.tsx` 直接渲染 `ChatInterface`，属于单页壳模式。

* `ChatInterface.tsx` 已经具备 chat / bi 两种模式切换能力，但仍然是本地 state 切换，不是真正的独立路由。

* `BiArea.tsx` 当前深度绑定旧后端协议：

  * 依赖旧 credential service

  * 依赖旧 `EventSource` + `GET /api/bi/query`

  * 依赖旧 `trace/clarification/intent/result` 事件结构

* 当前 `src/utils/sse.ts` 仅封装了 `EventSource` 读取，不适合新的 `POST + fetch stream` BI 协议。

#### 前端架构结论

* V1 不建议“每个 agent 一个独立 app”。

* V1 采用“一个前端 app + 多 agent 独立路由”模式。

* 推荐路由结构：

  * `/chat`

  * `/bi`

* 共用部分保留在同一个 app 内：

  * `Sidebar`

  * 主题切换

  * 全局布局壳

  * 公共输入组件风格

#### 为什么不建议多 app 模式

* 当前 monorepo 已经只有一个前端 app，继续扩展路由成本最低。

* chat 与 bi 共享视觉壳、侧边栏、主题、设置入口，如果拆成多个 app，会重复建设和重复发布。

* 多 app 会带来额外问题：

  * 跨 app 导航与登录态同步

  * 配置与环境变量重复

  * 组件样式与设计系统漂移

  * 部署入口增多

* 只有当未来 agent 之间在产品边界、租户隔离、部署周期、权限体系上明显独立时，才值得拆成多 app。

#### V1 推荐前端目录演进

```text
apps/ai4j-factory-ui/src/app
├── layout.tsx
├── page.tsx                -> 重定向到 /chat
├── chat
│   └── page.tsx
└── bi
    └── page.tsx

apps/ai4j-factory-ui/src/components
├── layout
│   ├── AppShell.tsx
│   └── Sidebar.tsx
├── chat
│   ├── ChatPage.tsx
│   ├── ChatArea.tsx
│   └── MessageList.tsx
├── bi
│   ├── BiPage.tsx
│   ├── BiComposer.tsx
│   ├── BiMessageThread.tsx
│   ├── BiResultCard.tsx
│   ├── BiStatusTimeline.tsx
│   └── BiChartPanel.tsx
└── shared
    ├── ChatInput.tsx
    ├── Markdown.tsx
    └── EmptyState.tsx
```

#### 页面组织策略

* `ChatInterface.tsx` 不再作为长期中心组件。

* 将其职责拆解为：

  * 路由级页面组件

  * 布局壳组件

  * agent 专属内容组件

* `Sidebar` 从“切换本地 mode”改为“路由导航”：

  * `New Chat` -> `/chat`

  * `BI Agent` -> `/bi`

* `Recent` 在 BI V1 中不保留服务端历史会话能力，也不要求本地 recent 支持 BI 恢复。

#### BI 页面规划

* `BiPage.tsx`

  * 路由级入口，负责装配 BI 页面上下文。

* `BiComposer.tsx`

  * 负责输入框、提交、禁用状态。

* `BiMessageThread.tsx`

  * 负责渲染用户问题、状态消息、流式回答。

* `BiStatusTimeline.tsx`

  * 展示阶段性状态，如 `analyzing`、`planning`、`querying`、`answering`。

* `BiResultCard.tsx`

  * 展示最终 summary、SQL 预览、表格、错误信息。

* `BiChartPanel.tsx`

  * 负责图表渲染与切换。

#### 模型选择区规划

* BI V1 前端先隐藏 provider/model 选择器。

* BI 页面对用户只暴露一个默认可用模型，不让用户在首期承担模型选择复杂度。

* 因此：

  * `ChatInput.tsx` 需要支持“可选 model picker”模式。

  * BI 页面可复用输入框样式，但隐藏下拉选择区。

* 后端仍保留 provider/model 配置能力，只是不在 V1 BI 页面公开。

#### 会话与历史规划

* BI V1 暂不支持历史会话恢复。

* 不再沿用当前 `useRecentSessions` 对 BI recent 的写入方式作为正式方案。

* `/bi` 页面刷新后按“新会话”处理即可。

* 若后续进入 V2，再为 BI 增加：

  * conversationId

  * query history

  * result replay

#### SSE / Stream 读取升级

* Chat 可暂时保留现有 `EventSource` 方案。

* BI 新协议必须改为 `fetch + ReadableStream`，因为：

  * 需要 `POST /api/bi/query`

  * 未来可能携带更复杂请求体

  * 更利于控制 abort / timeout / headers

* 因此前端新增专用流式客户端：

  * `src/services/biStreamClient.ts`

* 职责：

  * 发送 `POST /api/bi/query`

  * 逐块读取 SSE 文本

  * 解析 `data:` 行

  * 分发 `status/chunk/result/error/done`

#### 前端状态模型

* BI 页面不再延续当前过重的 `BiArea` 单组件大状态。

* 建议拆成以下状态：

  * `input state`

  * `request state`

  * `stream state`

  * `result state`

* 可用一个页面级 reducer 管理，避免多个 `useState` 散落导致可维护性下降。

#### 前端实施顺序

1. 将首页拆为 `/chat` 与 `/bi` 独立路由
2. 抽离 `AppShell` 与路由化 `Sidebar`
3. 复用现有 chat 页面，最小改造
4. 重写 BI 页面，不沿用旧 `BiArea` 作为最终形态
5. 新增 `biStreamClient.ts`，接入新 BI SSE 协议
6. 隐藏 BI 模型选择区
7. 移除 BI recent history 正式依赖

### 9. 测试策略

#### 单元测试

* `domain` 层：

  * 语义模型校验

  * QueryIntent / QueryPlan 约束

* `infrastructure/sql`：

  * SQL 生成正确性

  * 参数绑定顺序

  * limit / operator 白名单

* `application/orchestrator`：

  * 五阶段顺序

  * 错误分支

  * 事件时序

#### 契约测试

* `api/http/BiQueryControllerTest`

  * `POST` 返回 `text/event-stream`

  * 事件顺序正确

  * `done` 结尾

* `SseEventEncoderTest`

  * SSE 格式正确

  * `chunk` 事件不被合并

#### 适配器测试

* `DashScopeModelGatewayTest`

* `OpenAiCompatibleModelGatewayTest`

* `YamlSemanticCatalogLoaderTest`

* `JdbcWarehouseQueryAdapterTest`

#### 端到端验证

* 用一份示例语义层 + 测试数据库跑通完整查询。

* 验证浏览器端能看到真正的逐段打字效果。

### 10. 迁移与替代策略

#### 阶段 1: 新模块落地

* 新建 `ai4j-agent-bi-service`，不改旧 `ai4j-factory-service` 的 BI 代码。

#### 阶段 2: 跑通新链路

* 完成新协议、语义层、SQL 规划、查询、洞察、SSE。

#### 阶段 3: 对接前端

* 前端改接新 `POST /api/bi/query` 协议。

* 前端按 `status/chunk/result/error/done` 渲染，不依赖旧事件模型。

#### 阶段 4: 旧 BI 下线

* 当新模块功能覆盖完成后，停止继续演进 `services/ai4j-factory-service` 下的 BI 代码。

* 再评估是否删除旧 `org.ai4j.factory.bi/*` 与 `org.ai4j.factory.sse/*`。

## Assumptions & Decisions

* Maven 模块名确定为 `ai4j-agent-bi-service`。

* 包名确定为 `org.ai4j.agent.bi`。

* 新项目位于 `services/agent` 下，最终目标是替代旧 BI，而不是继续在 `ai4j-factory-service` 上打补丁。

* 首期范围是完整链路，不是 demo，也不是只做编排内核。

* 模型配置采用“配置文件优先”，不建设 DB 配置中心。

* 语义层保留，但模型只负责意图，不负责直接写 SQL。

* 新接口允许升级，不受旧 `/api/bi/query` GET 风格与旧事件 envelope 约束。

* SSE 打字机效果是硬要求，必须从后端增量发送。

* 前端采用“一个 app + 多 agent 独立路由”，而不是“每个 agent 一个独立 app”。

* BI V1 前端隐藏模型选择器，默认走后端配置的可用模型。

* BI V1 不支持历史会话恢复，先聚焦当前会话与完整查询链路。

* `ai4j-agent-assistbot` 继续保留为 demo/sandbox，不与 `ai4j-agent-bi-service` 混合职责。

* 旧 `ai4j-factory-service` 的 BI 代码已被废弃，严禁在开发新模块时参照或复制其代码实现。旧代码仅作为理解历史问题和功能边界的对照，不能作为新架构的模板。

## Verification Steps

### 构建与依赖

* 根聚合能识别 `services/agent/ai4j-agent-bi-service`。

* `mvn -pl services/agent/ai4j-agent-bi-service -am test` 通过。

* `mvn -pl services/agent/ai4j-agent-bi-service -am spring-boot:run` 可启动。

### 协议与流式输出

* 使用 `curl` 或前端 `fetch` 调用 `POST /api/bi/query`，响应头为 `text/event-stream`。

* 观察 `status -> chunk -> result -> done` 的事件顺序。

* 观察 `chunk` 为连续增量输出，而不是一次性整段返回。

### SQL 与语义安全

* 对未声明 metric/dimension 的请求返回可解释错误。

* 所有 SQL 均为参数化，不出现原始输入直接拼接。

* 结果集默认带 limit。

### 兼容模型

* DashScope 模式可正常生成洞察。

* OpenAI-compatible 模式可通过统一端口接入。

* 更换 provider 不需要修改应用层编排代码。

### 可维护性

* 控制器、编排器、SQL 规划器、模型适配器、JDBC 适配器均可独立测试。

* 任一层替换时，不需要修改其他层的核心接口。

## Implementation Order

1. 调整根与 `services/agent` 聚合 `pom.xml`
2. 新建 `services/agent/ai4j-agent-bi-service` 模块骨架
3. 落地 `domain` 与 `application.port`
4. 落地 `semantic` 加载器与 `sql planner`
5. 落地 `ModelGatewayRegistry` 与两类模型适配器
6. 落地 `BiAgentOrchestrator` 与流式事件服务
7. 暴露 `POST /api/bi/query` SSE 接口
8. 前端拆分 `/chat` 与 `/bi` 独立路由
9. 抽离 `AppShell`、路由化 `Sidebar` 与共享输入组件
10. 重写 BI 页面并接入 `fetch + ReadableStream` 新协议
11. 补齐单元测试、契约测试、端到端验证
12. 前端切换完成后，再计划旧 BI 下线
