## ADDED Requirements

### Requirement: Semantic layer definition file

The system SHALL load semantic layer definitions from JSON files in `src/main/resources/semantic/`. Each file defines one or more subjects (business topics), each containing a table mapping, atomic metrics, and dimensions.

#### Scenario: Load valid semantic layer file

- **WHEN** the application starts and a valid `orders.json` file exists in `src/main/resources/semantic/`
- **THEN** the system loads all subjects, metrics, and dimensions into memory
- **THEN** the loaded semantic layer is available for intent extraction and SQL building

#### Scenario: Reject invalid semantic layer file

- **WHEN** a semantic layer JSON file contains a subject without a `table` field
- **THEN** the application fails to start with a clear error message indicating the missing field and file name

### Requirement: Subject definition

Each subject in the semantic layer SHALL have a name, a target database table, a description for LLM matching, and lists of metrics and dimensions.

#### Scenario: Define a subject with metrics and dimensions

- **WHEN** a subject is defined with `name: "订单分析"`, `table: "orders"`, and at least one metric and one dimension
- **THEN** the subject is valid and can be used for query construction

### Requirement: Atomic metric definition

Each metric SHALL define a name (Chinese display name), a column reference, an aggregation function (SUM, COUNT, AVG, MAX, MIN), and a description. No derived metrics in V1.

#### Scenario: Define atomic metric with SUM aggregation

- **WHEN** a metric is defined with `name: "销售额"`, `column: "amount"`, `aggregation: "SUM"`
- **THEN** the system maps this metric to `SUM(amount)` in SQL generation

#### Scenario: Reject unsupported aggregation

- **WHEN** a metric specifies an aggregation function other than SUM, COUNT, AVG, MAX, or MIN
- **THEN** the system rejects the configuration at load time with an error message

### Requirement: Dimension definition

Each dimension SHALL define a name (Chinese display name), a column reference, and a data type (`string`, `number`, or `time`).

#### Scenario: Define a string dimension

- **WHEN** a dimension is defined with `name: "区域"`, `column: "region"`, `type: "string"`
- **THEN** the dimension is available for GROUP BY and WHERE filtering in SQL generation

### Requirement: LLM-based intent extraction

The system SHALL use an LLM to convert a user's natural language question into a structured `QueryIntent` object containing subject name, metric names, dimension names, and optional filters. The LLM prompt SHALL include a summary of the loaded semantic layer as context.

#### Scenario: Extract intent for a simple metric query

- **WHEN** the user asks "订单销售额是多少" and the semantic layer contains subject "订单分析" with metric "销售额"
- **THEN** the system returns a QueryIntent with `subject: "订单分析"`, `metrics: ["销售额"]`, and empty dimensions and filters

#### Scenario: Extract intent with dimension grouping

- **WHEN** the user asks "按区域统计销售额" and the semantic layer has dimension "区域"
- **THEN** the system returns a QueryIntent with `subject: "订单分析"`, `metrics: ["销售额"]`, `dimensions: ["区域"]`

#### Scenario: Extract intent with filter condition

- **WHEN** the user asks "华东区的销售额" and the semantic layer has dimension "区域"
- **THEN** the system returns a QueryIntent with a filter `{"dimension": "区域", "operator": "=", "value": "华东"}`

#### Scenario: Unsupported query rejection

- **WHEN** the user asks about a concept not defined in the semantic layer
- **THEN** the LLM responds indicating the query cannot be answered with available data

### Requirement: Structured intent validation

The system SHALL validate the LLM's JSON output before accepting it as a QueryIntent. Validation checks that the subject name, metric names, and dimension names all exist in the loaded semantic layer.

#### Scenario: Valid intent passes validation

- **WHEN** the LLM returns a QueryIntent where all referenced subjects, metrics, and dimensions exist in the semantic layer
- **THEN** the intent is accepted and passed to the query execution stage

#### Scenario: Invalid metric name rejected

- **WHEN** the LLM returns a QueryIntent with a metric name not registered in the semantic layer
- **THEN** the system retries the LLM call with a correction prompt, or returns an error after max retries

### Requirement: SQL generation from QueryIntent

The system SHALL generate a parameterized SQL SELECT statement from a validated QueryIntent and the semantic layer mappings. The SQL SHALL use JDBC PreparedStatement placeholders for filter values to prevent injection.

#### Scenario: Generate SQL for a single metric without dimensions

- **WHEN** QueryIntent specifies subject "订单分析" with metric "销售额" and no dimensions
- **THEN** the generated SQL is `SELECT SUM(amount) AS 销售额 FROM orders` with no GROUP BY clause

#### Scenario: Generate SQL with metric and dimension grouping

- **WHEN** QueryIntent specifies metric "销售额" and dimension "区域"
- **THEN** the generated SQL is `SELECT region AS 区域, SUM(amount) AS 销售额 FROM orders GROUP BY region`

#### Scenario: Generate SQL with filter condition

- **WHEN** QueryIntent includes filter `{"dimension": "区域", "operator": "=", "value": "华东"}`
- **THEN** the generated SQL includes `WHERE region = ?` with "华东" as a PreparedStatement parameter

#### Scenario: Default result limit

- **WHEN** any SQL is generated
- **THEN** the SQL SHALL include `LIMIT 100` to prevent excessive result sets

### Requirement: Query execution against MySQL

The system SHALL execute the generated parameterized SQL against the configured MySQL database and return results as a list of column-name-to-value maps.

#### Scenario: Execute a valid query

- **WHEN** a valid parameterized SQL is executed against a MySQL database with matching tables
- **THEN** the system returns `List<Map<String, Object>>` where each map represents one row with column names as keys

#### Scenario: Handle empty result set

- **WHEN** the executed query returns zero rows
- **THEN** the system returns an empty list and signals that no data was found

#### Scenario: Handle database connection failure

- **WHEN** the MySQL database is unreachable
- **THEN** the system returns an error with a clear message and does not crash the application

### Requirement: LLM insight generation from query results

The system SHALL use an LLM to interpret the structured query results and generate a human-readable insight with chart type recommendation.

#### Scenario: Generate insight for a single-value result

- **WHEN** query results contain a single row with metric "销售额" = 12800000
- **THEN** the LLM generates a textual summary describing the value
- **THEN** the insight includes a chart recommendation

#### Scenario: Generate insight for grouped results

- **WHEN** query results contain multiple rows grouped by "区域" with "销售额" values
- **THEN** the LLM generates a textual summary comparing values across groups
- **THEN** the insight includes a chart recommendation (e.g., "bar")

#### Scenario: Generate insight for empty results

- **WHEN** query results are empty
- **THEN** the LLM generates a message indicating no data matched the query conditions

### Requirement: Insight response format

The system SHALL return insights in a structured format containing the original question, text summary, raw data, and chart recommendation.

#### Scenario: Complete insight response

- **WHEN** insight generation completes successfully
- **THEN** the response includes `question`, `summary`, `data`, and `chartType` fields

### Requirement: BI API endpoint

The system SHALL expose a BI query endpoint at `/api/bi/query` that accepts a user question and returns an insight response.

#### Scenario: BI query with valid question

- **WHEN** a POST request is made to `/api/bi/query` with `{"question": "华东区销售额多少"}`
- **THEN** the system runs the full pipeline (intent → SQL → query → insight) and returns an `InsightResponse`

### Requirement: BI module uses shared credentials

The BI module SHALL resolve LLM credentials from the shared credential store for both intent extraction and insight generation.

#### Scenario: BI resolves credential for LLM calls

- **WHEN** a BI query request specifies a `credentialId`
- **THEN** the BI module fetches the credential from `ModelCredentialRepository`
- **THEN** it configures LLM clients for both intent extraction and insight generation using the same credential
