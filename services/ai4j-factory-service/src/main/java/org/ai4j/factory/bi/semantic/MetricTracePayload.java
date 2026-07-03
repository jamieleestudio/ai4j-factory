package org.ai4j.factory.bi.semantic;

public record MetricTracePayload(
        String name,
        String description,
        String aggregation
) {
}
