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

Each metric SHALL define a name (Chinese display name), a column reference, an aggregation function (SUM, COUNT, AVG, MAX, MIN), and a description. No derived metrics (formulas combining other metrics) in V1.

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
