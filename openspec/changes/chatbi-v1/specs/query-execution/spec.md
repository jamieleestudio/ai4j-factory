## ADDED Requirements

### Requirement: SQL generation from QueryIntent

The system SHALL generate a parameterized SQL SELECT statement from a validated QueryIntent and the semantic layer mappings. The SQL SHALL use JDBC PreparedStatement placeholders for filter values to prevent injection.

#### Scenario: Generate SQL for a single metric without dimensions

- **WHEN** QueryIntent specifies subject "订单分析" with metric "销售额" and no dimensions
- **THEN** the generated SQL is `SELECT SUM(amount) AS 销售额 FROM orders` with no GROUP BY clause

#### Scenario: Generate SQL with metric and dimension grouping

- **WHEN** QueryIntent specifies metric "销售额" and dimension "区域"
- **THEN** the generated SQL is `SELECT region AS 区域, SUM(amount) AS 销售额 FROM orders GROUP BY region`

#### Scenario: Generate SQL with multiple metrics and dimensions

- **WHEN** QueryIntent specifies metrics ["销售额", "订单量"] and dimensions ["区域"]
- **THEN** the generated SQL includes both aggregations and the GROUP BY clause

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
- **THEN** the system returns an empty list (not an error) and signals to the insight generation stage that no data was found

#### Scenario: Handle database connection failure

- **WHEN** the MySQL database is unreachable
- **THEN** the system returns an error with a clear message and does not crash the application
