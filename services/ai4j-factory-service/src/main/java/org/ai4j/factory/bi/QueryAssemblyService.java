package org.ai4j.factory.bi;

import org.ai4j.factory.bi.intent.IntentExtractionResult;
import org.ai4j.factory.bi.intent.QueryIntent;
import org.ai4j.factory.bi.query.SqlBuilder;
import org.ai4j.factory.bi.semantic.Dimension;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.sse.DimensionRef;
import org.ai4j.factory.sse.IntentEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryAssemblyService {

    private final SemanticLayer semanticLayer;
    private final SqlBuilder sqlBuilder;

    public QueryAssemblyService(SemanticLayer semanticLayer, SqlBuilder sqlBuilder) {
        this.semanticLayer = semanticLayer;
        this.sqlBuilder = sqlBuilder;
    }

    public BiQueryPlan assemble(IntentExtractionResult.Ready ready) {
        QueryIntent intent = new QueryIntent();
        intent.setSubject(ready.subject());
        intent.setMetrics(new ArrayList<>(ready.metrics()));
        intent.setDimensions(new ArrayList<>(ready.dimensions()));
        intent.setFilters(new ArrayList<>(ready.filters()));

        Subject subject = semanticLayer.getSubject(intent.getSubject());
        return new BiQueryPlan(intent, subject, sqlBuilder.build(intent, subject));
    }

    public IntentEvent toIntentEvent(BiQueryPlan plan) {
        List<Map<String, Object>> filters = plan.intent().getFilters().stream()
                .map(filter -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("dimension", filter.getDimension());
                    values.put("operator", filter.getOperator());
                    values.put("value", filter.getValue());
                    return values;
                })
                .toList();

        List<DimensionRef> dimensions = plan.intent().getDimensions().stream()
                .map(name -> toDimensionRef(plan.subject(), name))
                .toList();

        return new IntentEvent(plan.intent().getSubject(), plan.intent().getMetrics(), dimensions, filters);
    }

    private DimensionRef toDimensionRef(Subject subject, String dimensionName) {
        Dimension dimension = subject.getDimensions().stream()
                .filter(item -> item.getName().equals(dimensionName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Dimension not found: " + dimensionName));
        return new DimensionRef(dimensionName, dimension.getType().name());
    }
}
