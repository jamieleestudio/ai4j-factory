package org.ai4j.factory.bi.semantic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.*;

public class SemanticLayer {

    private final Map<String, Subject> subjectsByName = new LinkedHashMap<>();

    public void loadFromResources(String locationPattern) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(locationPattern);

        for (Resource resource : resources) {
            List<Subject> subjects = mapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<Subject>>() {}
            );
            for (Subject subject : subjects) {
                validate(subject);
                subjectsByName.put(subject.getName(), subject);
            }
        }
    }

    private void validate(Subject subject) {
        if (subject.getName() == null || subject.getName().isBlank()) {
            throw new IllegalArgumentException("Subject name is required");
        }
        if (subject.getTable() == null || subject.getTable().isBlank()) {
            throw new IllegalArgumentException("Subject '" + subject.getName() + "' must have a table");
        }
        if (subject.getMetrics() != null) {
            for (Metric metric : subject.getMetrics()) {
                if (metric.getName() == null || metric.getColumn() == null || metric.getAggregation() == null) {
                    throw new IllegalArgumentException(
                            "Metric in subject '" + subject.getName() + "' must have name, column, and aggregation");
                }
            }
        }
        if (subject.getDimensions() != null) {
            for (Dimension dim : subject.getDimensions()) {
                if (dim.getName() == null || dim.getColumn() == null || dim.getType() == null) {
                    throw new IllegalArgumentException(
                            "Dimension in subject '" + subject.getName() + "' must have name, column, and type");
                }
            }
        }
    }

    public Subject getSubject(String name) {
        Subject subject = subjectsByName.get(name);
        if (subject == null) {
            throw new IllegalArgumentException("Subject not found: " + name);
        }
        return subject;
    }

    public Collection<Subject> getAllSubjects() {
        return Collections.unmodifiableCollection(subjectsByName.values());
    }

    public List<SubjectTracePayload> toTracePayload() {
        return subjectsByName.values().stream()
                .map(s -> new SubjectTracePayload(
                        s.getName(),
                        s.getDescription(),
                        s.getMetrics() == null ? List.of() : s.getMetrics().stream()
                                .map(m -> new MetricTracePayload(
                                        m.getName(),
                                        m.getDescription(),
                                        m.getAggregation() == null ? null : m.getAggregation().name()
                                ))
                                .toList(),
                        s.getDimensions() == null ? List.of() : s.getDimensions().stream()
                                .map(d -> new DimensionTracePayload(
                                        d.getName(),
                                        d.getType() == null ? null : d.getType().name()
                                ))
                                .toList()
                ))
                .toList();
    }

    public String toPromptSummary() {
        StringBuilder sb = new StringBuilder();
        for (Subject subject : subjectsByName.values()) {
            sb.append("主题: ").append(subject.getName())
                    .append(" (").append(subject.getDescription()).append(")\n");
            if (subject.getMetrics() != null) {
                sb.append("  指标: ");
                for (Metric m : subject.getMetrics()) {
                    sb.append(m.getName()).append("(").append(m.getDescription()).append("), ");
                }
                sb.setLength(sb.length() - 2);
                sb.append("\n");
            }
            if (subject.getDimensions() != null) {
                sb.append("  维度: ");
                for (Dimension d : subject.getDimensions()) {
                    sb.append(d.getName()).append("(").append(d.getType()).append("), ");
                }
                sb.setLength(sb.length() - 2);
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
