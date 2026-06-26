## Context

当前 `ai4j-factory` 已有通用对话服务 (`ai4j-chatbot`)，但缺少数据问答能力。ChatBI V1 作为新服务加入，目标是跑通「自然语言提问 → 结构化数据查询 → 洞察交付」的核心链路。

约束：语义层用 JSON 文件手动配置，先配一张业务表；时间解析、权限注入、前端 UI 均不在 V1 范围。

## Goals / Non-Goals

**Goals:**
- 新建 `ai4j-chatbi` 服务，独立部署
- JSON 语义层定义主题、原子指标、维度
- LLM 意图提取：用户自然语言 → 结构化查询参数
- 系统根据语义层拼装参数化 SQL 并执行
- LLM 解读查询结果，生成洞察和图表建议

**Non-Goals:**
- 时间解析（"上个月"、"去年Q4"）— 专题处理
- 权限注入 — 后续版本
- 前端 UI — 语义层稳定后再做
- 下钻、Top N、派生指标、跨主题关联
- 多数据源、缓存、性能优化

## Decisions

### 1. 语义层存储：JSON 文件

使用 JSON 文件定义语义层，放在 `src/main/resources/semantic/` 下，Spring Boot 启动时加载到内存。

**理由**：V1 手动配一张表，JSON 直观、零依赖、易调试。后续可迁移到 DB 或管理后台。

**替代方案**：DB 表存储 — 灵活但 V1 过度设计；YAML — 等价，JSON 在 Java 生态解析更方便。

### 2. SQL 生成：系统拼装，LLM 不写 SQL

意图提取阶段 LLM 输出结构化参数（主题名、指标名、维度名、过滤条件），系统根据语义层映射拼装 SQL。使用 JDBC PreparedStatement 参数化查询。

**理由**：消除 SQL 注入风险，避免 LLM 幻觉生成错误 SQL。语义层是 LLM 与数据之间的安全边界。

**替代方案**：Text-to-SQL — 灵活但需要复杂的安全校验和 SQL 解析，V1 阶段风险大于收益。

### 3. 意图提取：LLM 返回结构化 JSON

LLM 接收用户问题 + 语义层摘要作为 context，返回 JSON 格式的结构化意图。

```
用户输入："华东区销售额多少"
LLM 输出：{"subject": "订单分析", "metrics": ["销售额"], "dimensions": ["区域"], "filters": [{"dimension": "区域", "operator": "=", "value": "华东"}]}
```

**理由**：LLM 擅长 NLU 但不适合生成 SQL；结构化 JSON 可校验、可追踪。

### 4. 查询结果格式

查询执行返回结构化数据：`List<Map<String, Object>>`（列名 → 值），传递给洞察生成阶段。

**理由**：通用格式，不绑定特定表结构；LLM 可以直接解读列名和数值。

### 5. 服务间复用：独立服务，不共享代码

ChatBI 与 Chatbot 各自独立，ChatBI 直接依赖 Spring AI 和 MySQL。不抽取共享模块。

**理由**：V1 阶段两服务无共享需求（Chatbot 管理模型凭证，ChatBI 直连 LLM），强行共享增加耦合。后续如有共性再提取。

### 6. 模块命名和包结构

Maven 模块：`services/ai4j-chatbi`，包路径：`org.ai4j.chatbi`

```
services/ai4j-chatbi/
├── pom.xml
└── src/main/
    ├── java/org/ai4j/chatbi/
    │   ├── ChatBiApplication.java
    │   ├── config/
    │   │   └── SemanticLayerConfig.java    # 加载 JSON 语义层
    │   ├── semantic/
    │   │   ├── SemanticLayer.java          # 语义层模型
    │   │   └── Subject.java                # 主题（含指标、维度）
    │   ├── intent/
    │   │   ├── IntentExtractionService.java # LLM 意图提取
    │   │   └── QueryIntent.java             # 结构化意图模型
    │   ├── query/
    │   │   ├── SqlBuilder.java              # 语义层 → SQL
    │   │   └── QueryExecutionService.java   # 执行查询
    │   ├── insight/
    │   │   └── InsightGenerationService.java # LLM 洞察生成
    │   └── controller/
    │       └── ChatBiController.java        # REST API
    └── resources/
        ├── application.yml
        └── semantic/
            └── orders.json                  # 语义层定义
```

## Risks / Trade-offs

- **语义层覆盖不足** → LLM 意图提取时可能匹配失败。缓解：语义层定义纳入 LLM prompt 作为 context，LLM 可回答"不支持该查询"。
- **LLM 意图提取不稳定** → 同一问题可能输出不同 JSON。缓解：输出 schema 约束 + 校验 + 重试。
- **大结果集** → 查询可能返回大量行。缓解：V1 默认 LIMIT 100，后续加分页。
- **数据库密码明文** → application.yml 中配置。缓解：V1 开发环境可接受，生产需环境变量或配置中心。
