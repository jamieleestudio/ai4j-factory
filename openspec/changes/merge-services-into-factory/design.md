## Context

当前 `ai4j-factory` 有两个独立后端服务：
- `ai4j-chatbot`（端口 8080）：通用流式聊天 + 凭证管理（ModelProvider, ModelCredential, ModelConfig, UserConfig）
- `ai4j-chatbi`（端口 8081）：BI 数据分析（语义层、意图提取、SQL 生成、查询执行、洞察生成）

前端 `apps/ai4j-factory-ui` 目前只有聊天界面，侧边栏仅有 "New Chat" 入口。

目标：合并为一个 `ai4j-factory-service`，前端增加 BI 菜单。用户在一个应用内通过菜单切换聊天和 BI 功能。

## Goals / Non-Goals

**Goals:**
- 合并两个 Maven 模块为 `services/ai4j-factory-service`
- 包结构清晰隔离：`shared/`（凭证、LLM 客户端）、`chat/`（流式聊天）、`bi/`（BI Agent）
- 共享凭证管理：chat 和 bi 使用同一套 ModelProvider / ModelCredential / ModelConfig
- 前端侧边栏增加 "BI" 菜单，点击进入 BI 分析界面
- 保留 chatbi-v1 所有功能规格（语义层、意图提取、SQL 执行、洞察生成）

**Non-Goals:**
- 不实现智能路由（菜单直连对应 API，用户自行选择模式）
- 不修改 chatbi-v1 的业务逻辑规格，仅为代码迁移
- 不抽取独立 shared 库（shared 只是 `ai4j-factory-service` 内的一个包）
- 不保留旧模块的 Git 历史迁移（代码移动即可）

## Decisions

### 1. 包结构

```
services/ai4j-factory-service/
└── src/main/java/org/ai4j/factory/
    ├── FactoryApplication.java          ← @SpringBootApplication
    ├── shared/
    │   ├── credential/                  ← 凭证管理
    │   │   ├── entity/                  ← ModelProvider, ModelCredential, ModelConfig, UserConfig
    │   │   ├── repository/              ← JPA Repository
    │   │   ├── service/                 ← CRUD Service
    │   │   ├── controller/              ← REST Controller (settings API)
    │   │   └── dto/                     ← Response DTO
    │   └── llm/                         ← LLM 客户端封装（共用）
    ├── chat/
    │   ├── ChatController.java          ← /api/chat/stream
    │   └── ChatService.java             ← 流式聊天逻辑
    └── bi/
        ├── BiController.java            ← /api/bi/query
        ├── semantic/                    ← 语义层模型 + 加载
        ├── intent/                      ← 意图提取
        ├── query/                       ← SQL 构建 + 执行
        └── insight/                     ← 洞察生成
```

**理由**：`shared/` 是包而非独立模块，零依赖开销。chat 和 bi 各自独立，互不引用对方的包。

**替代方案**：抽取 `ai4j-shared` 独立 Maven 模块 — V1 过度设计，一个 shared 包足够。

### 2. 数据库合并

合并为单一数据库 `ai4j_factory`，包含：
- 原 chatbot 的表：`model_provider`, `model_credential`, `model_config`, `user_config`
- chatbi-v1 未建表（语义层用 JSON 文件，无业务表）

Flyway 迁移脚本重新编号，统一放在 `db/migration/` 下。

**理由**：单服务单库，运维简单。chatbi V1 没有业务数据表，迁移无数据丢失风险。

### 3. Controller 路径

| 模块 | 路径 | 说明 |
|------|------|------|
| Chat | `/api/chat/stream` | SSE 流式聊天 |
| BI | `/api/bi/query` | BI 查询（POST） |
| Settings | `/api/settings/providers` 等 | 凭证管理（从 `/api/model-providers` 迁移） |

Setting 的 API 路径从 `/api/model-providers` 改为 `/api/settings/providers`，更语义化。

**理由**：路径前缀清晰对应前端菜单，`/api/settings/` 统一管理后台配置。

### 4. 前端路由

侧边栏菜单结构：
```
├── 🆕 New Chat   → /chat   → 调用 /api/chat/stream
├── 📊 BI         → /bi     → 调用 /api/bi/query
└── ⚙️ Settings   → Modal   → 调用 /api/settings/*
```

使用 Next.js 客户端路由或状态切换（不引入 react-router），BI 界面复用现有聊天界面的布局框架（侧边栏 + 内容区）。

**理由**：两个界面共享布局组件（Sidebar + 内容区），只是内容区渲染不同的组件。

### 5. Maven 模块命名

| 项目 | Maven artifact | 文件夹 |
|------|---------------|--------|
| 后端 | `ai4j-factory-service` | `services/ai4j-factory-service` |
| 前端 | `ai4j-factory-ui` | `apps/ai4j-factory-ui`（已存在） |

**理由**：前后端命名对齐，`-service` 后缀明确表示后端服务。

## Risks / Trade-offs

- **凭证 API 路径变更** → `/api/model-providers` 改为 `/api/settings/providers`，前端需同步更新。缓解：前端 CredentialManager 集中修改。
- **端口合并** → chatbi 原来独立端口 8081，合并后统一 8080。如果之前有外部系统调 chatbi API，需要更新 URL。
- **单点故障** → 合并后 chat 和 bi 共享进程，一个模块的 bug 可能影响另一个。V1 阶段可接受，后续可拆分。

## Migration Plan

1. 新建 `services/ai4j-factory-service` 模块
2. 迁移代码（不删除旧模块）→ 编译通过
3. 更新前端 API 路径
4. 端到端验证
5. 删除旧模块 `ai4j-chatbot`、`ai4j-chatbi`
6. 更新根 `pom.xml`

回滚：在步骤 5 之前，旧模块仍可用。步骤 5 之后通过 Git revert。

## Open Questions

<!-- None -->
