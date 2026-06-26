## 1. Project Setup

- [x] 1.1 Create `services/ai4j-chatbi` Maven module with `pom.xml` (Spring Boot Web, Spring AI Open AI Starter, MySQL Connector, Flyway)
- [x] 1.2 Register module in root `pom.xml` (`<module>services/ai4j-chatbi</module>`)
- [x] 1.3 Create `ChatBiApplication.java` main class with `@SpringBootApplication`
- [x] 1.4 Create `application.yml` with MySQL datasource config, server port, and Spring AI settings

## 2. Semantic Layer

- [x] 2.1 Create `Subject` model class (name, table, description, List of Metric, List of Dimension)
- [x] 2.2 Create `Metric` model class (name, column, aggregation enum, description)
- [x] 2.3 Create `Dimension` model class (name, column, type enum)
- [x] 2.4 Create `SemanticLayer` class (loads JSON files from `resources/semantic/`, provides lookup methods by name)
- [x] 2.5 Create `orders.json` semantic layer definition file in `resources/semantic/` with sample metrics (销售额/SUM, 订单量/COUNT) and dimensions (区域, 产品线, 下单时间)
- [x] 2.6 Create `SemanticLayerConfig` to load and validate semantic layer at startup, fail-fast on invalid config

## 3. Intent Extraction

- [x] 3.1 Create `QueryIntent` model (subject, metrics list, dimensions list, filters list with dimension/operator/value)
- [x] 3.2 Create `Filter` model (dimension name, operator, value)
- [x] 3.3 Create `IntentExtractionService` — builds LLM prompt with semantic layer summary, calls LLM, parses JSON response into QueryIntent
- [x] 3.4 Add intent validation: verify all referenced subject/metric/dimension names exist in semantic layer, retry on mismatch

## 4. Query Execution

- [x] 4.1 Create `SqlBuilder` — converts QueryIntent + SemanticLayer into parameterized SQL (SELECT aggregates + GROUP BY dimensions + WHERE filters + LIMIT 100)
- [x] 4.2 Create `QueryExecutionService` — executes parameterized SQL via JdbcTemplate, returns `List<Map<String, Object>>`
- [x] 4.3 Handle edge cases: empty result set, DB connection failure

## 5. Insight Generation

- [x] 5.1 Create `InsightResponse` model (question, summary, data, chartType)
- [x] 5.2 Create `InsightGenerationService` — builds LLM prompt with original question + query results, returns structured insight with text summary and chart recommendation

## 6. API & Integration

- [x] 6.1 Create `ChatBiController` — POST `/api/chatbi/query` endpoint accepting user question, returning `InsightResponse`
- [x] 6.2 Wire full pipeline: controller → intent extraction → SQL build → query execution → insight generation → response
- [x] 6.3 Manual end-to-end test with curl/Postman: "华东区销售额多少" against a test orders table
