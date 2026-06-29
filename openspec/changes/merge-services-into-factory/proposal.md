## Why

当前 `ai4j-chatbot`（通用聊天）和 `ai4j-chatbi`（BI 数据分析）是两个独立服务，各自有独立的数据库、端口和部署单元。用户希望 ChatBI 作为 Agent 集成到统一聊天界面中，通过菜单切换而非部署两套系统。合并为单服务 `ai4j-factory-service` 降低运维复杂度，同时保持代码层面通过包清晰隔离。

## What Changes

- **BREAKING**: 删除 `services/ai4j-chatbot` 模块，代码迁移至 `services/ai4j-factory-service` 的 `chat/` 包
- **BREAKING**: 删除 `services/ai4j-chatbi` 模块，代码迁移至 `services/ai4j-factory-service` 的 `bi/` 包
- 新建 `services/ai4j-factory-service` Maven 模块，作为唯一后端服务
- 抽取共享代码至 `shared/` 包：凭证管理、LLM 客户端封装
- 合并数据库：两个服务的表合并到同一个 MySQL 数据库 `ai4j_factory`
- 前端 `apps/ai4j-factory-ui`：侧边栏增加 "BI" 菜单入口，与 "New Chat" 并列
- 更新根 `pom.xml`：移除旧模块声明，添加新模块
- 更新前端 `metadata.title` 为 "AI4J Factory"

## Capabilities

### New Capabilities

- `shared-credential`: 统一的 LLM 凭证管理，chat 和 bi 模块共用同一套凭证（供应商、凭证、模型配置 CRUD）
- `chat-streaming`: 通用流式聊天，用户选择凭证和模型后发起 SSE 流式对话
- `bi-agent`: BI 数据分析 Agent，包含语义层定义、LLM 意图提取、SQL 拼装执行、洞察生成

### Modified Capabilities

<!-- No existing capabilities to modify -->

## Impact

- 删除 2 个 Maven 模块：`services/ai4j-chatbot`、`services/ai4j-chatbi`
- 新增 1 个 Maven 模块：`services/ai4j-factory-service`
- 根 `pom.xml`：`<module>` 声明从 2 个改为 1 个
- 数据库：合并 `ai4j_chatbot` 和 `ai4j_chatbi` 为 `ai4j_factory`，Flyway 迁移脚本合并
- 前端：`apps/ai4j-factory-ui` 侧边栏增加 BI 菜单，API 调用路径更新
- 端口：原 chatbot(8080) 和 chatbi(8081) 合并为一个端口
