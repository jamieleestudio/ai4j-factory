package org.ai4j.factory.bi.semantic;

import java.util.List;

public record SubjectTracePayload(
        String name,
        String description,
        List<MetricTracePayload> metrics,
        List<DimensionTracePayload> dimensions
) {
}
