## ADDED Requirements

### Requirement: LLM insight generation from query results

The system SHALL use an LLM to interpret the structured query results and generate a human-readable insight. The LLM prompt SHALL include the original user question, the query results (as a table), and instructions to produce a textual summary and a chart type recommendation.

#### Scenario: Generate insight for a single-value result

- **WHEN** query results contain a single row with metric "销售额" = 12800000
- **THEN** the LLM generates a textual summary describing the value in a readable format (e.g., "销售额为 1,280 万元")
- **THEN** the insight includes a chart recommendation (e.g., "single_value" for a single number)

#### Scenario: Generate insight for grouped results

- **WHEN** query results contain multiple rows grouped by "区域" with "销售额" values
- **THEN** the LLM generates a textual summary comparing values across groups
- **THEN** the insight includes a chart recommendation (e.g., "bar" for categorical comparison)

#### Scenario: Generate insight for empty results

- **WHEN** query results are empty
- **THEN** the LLM generates a message indicating no data matched the query conditions

### Requirement: Insight response format

The system SHALL return insights in a structured format containing the original question, text summary, raw data, and chart recommendation.

#### Scenario: Complete insight response

- **WHEN** insight generation completes successfully
- **THEN** the response includes:
  - `question`: the original user question
  - `summary`: natural language text interpretation
  - `data`: the raw query results (for frontend table/chart rendering)
  - `chartType`: recommended chart type (one of: single_value, bar, line, pie)
