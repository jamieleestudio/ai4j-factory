## Why

传统数据查询需要写 SQL，业务人员无法自助完成。ChatBI 让用户用自然语言提问，系统自动完成从意图理解到洞察交付的全链路闭环。V1 目标是跑通核心链路：一张表 + 原子指标 + 维度，零 SQL 查数据。

## What Changes

- 新建 `services/ai4j-chatbi` 服务（Spring Boot + Spring AI）
- JSON 文件定义语义层（主题、原子指标、维度），手动配置一张业务表
- LLM 意图提取：自然语言 → 结构化查询意图（匹配语义层概念）
- 系统拼装 SQL：基于语义层映射，自动生成安全 SQL 并执行
- LLM 洞察生成：查询结果 → 文字解读 + 图表建议
- 在根 `pom.xml` 中添加新模块

## Capabilities

### New Capabilities

- `semantic-layer`: JSON 格式的语义层定义，包含主题（table 映射）、原子指标（column + aggregation）、维度（column + type），支持 LLM 意图匹配时检索
- `intent-extraction`: LLM 将用户自然语言问题转换为结构化意图（主题、指标、维度、过滤条件），限定在已注册的语义层范围内
- `query-execution`: 根据结构化意图和语义层映射，系统拼装参数化 SQL 并执行查询，返回结构化数据
- `insight-generation`: LLM 将查询结果解读为自然语言洞察，包含数据事实和图表类型建议

### Modified Capabilities

<!-- No existing capabilities to modify -->

## Impact

- 新增 Maven 模块 `services/ai4j-chatbi`，根 `pom.xml` 添加 `<module>` 声明
- 依赖：Spring Boot Web, Spring AI Open AI Starter, MySQL Connector, Flyway
- 新增数据库：`ai4j_chatbi`（独立于 chatbot 的数据库）
- 不影响现有 `ai4j-chatbot`、`agent`、`mcp` 服务
