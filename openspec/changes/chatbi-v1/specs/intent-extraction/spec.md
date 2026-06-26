## ADDED Requirements

### Requirement: LLM-based intent extraction

The system SHALL use an LLM to convert a user's natural language question into a structured `QueryIntent` object containing subject name, metric names, dimension names, and optional filters. The LLM prompt SHALL include a summary of the loaded semantic layer as context so the LLM can match user terms to registered concepts.

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

- **WHEN** the user asks about a concept not defined in the semantic layer (e.g., "客户满意度")
- **THEN** the LLM responds indicating the query cannot be answered with available data, listing the available subjects

### Requirement: Structured intent validation

The system SHALL validate the LLM's JSON output before accepting it as a QueryIntent. Validation checks that the subject name, metric names, and dimension names all exist in the loaded semantic layer.

#### Scenario: Valid intent passes validation

- **WHEN** the LLM returns a QueryIntent where all referenced subjects, metrics, and dimensions exist in the semantic layer
- **THEN** the intent is accepted and passed to the query execution stage

#### Scenario: Invalid metric name rejected

- **WHEN** the LLM returns a QueryIntent with a metric name not registered in the semantic layer
- **THEN** the system retries the LLM call with a correction prompt, or returns an error after max retries
