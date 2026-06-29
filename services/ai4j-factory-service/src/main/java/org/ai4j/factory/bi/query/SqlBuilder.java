package org.ai4j.factory.bi.query;

import org.ai4j.factory.bi.intent.Filter;
import org.ai4j.factory.bi.intent.QueryIntent;
import org.ai4j.factory.bi.semantic.Dimension;
import org.ai4j.factory.bi.semantic.Metric;
import org.ai4j.factory.bi.semantic.Subject;

import java.util.ArrayList;
import java.util.List;

public class SqlBuilder {

    public SqlResult build(QueryIntent intent, Subject subject) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<Object> params = new ArrayList<>();

        List<String> selectParts = new ArrayList<>();
        for (String dimName : intent.getDimensions()) {
            Dimension dim = findDimension(subject, dimName);
            selectParts.add(dim.getColumn() + " AS " + quoteIdentifier(dimName));
        }
        for (String metricName : intent.getMetrics()) {
            Metric metric = findMetric(subject, metricName);
            selectParts.add(metric.getAggregation().name() + "(" + metric.getColumn() + ") AS " + quoteIdentifier(metricName));
        }
        sql.append(String.join(", ", selectParts));
        sql.append(" FROM ").append(subject.getTable());

        if (intent.getFilters() != null && !intent.getFilters().isEmpty()) {
            List<String> whereParts = new ArrayList<>();
            for (Filter filter : intent.getFilters()) {
                Dimension dim = findDimension(subject, filter.getDimension());
                whereParts.add(dim.getColumn() + " " + filter.getOperator() + " ?");
                params.add(filter.getValue());
            }
            sql.append(" WHERE ").append(String.join(" AND ", whereParts));
        }

        if (!intent.getDimensions().isEmpty()) {
            List<String> groupParts = new ArrayList<>();
            for (String dimName : intent.getDimensions()) {
                Dimension dim = findDimension(subject, dimName);
                groupParts.add(dim.getColumn());
            }
            sql.append(" GROUP BY ").append(String.join(", ", groupParts));
        }

        sql.append(" LIMIT 100");

        return new SqlResult(sql.toString(), params.toArray());
    }

    private Metric findMetric(Subject subject, String name) {
        return subject.getMetrics().stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Metric not found: " + name));
    }

    private Dimension findDimension(Subject subject, String name) {
        return subject.getDimensions().stream()
                .filter(d -> d.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Dimension not found: " + name));
    }

    private String quoteIdentifier(String name) {
        return "`" + name + "`";
    }

    public record SqlResult(String sql, Object[] params) {}
}
