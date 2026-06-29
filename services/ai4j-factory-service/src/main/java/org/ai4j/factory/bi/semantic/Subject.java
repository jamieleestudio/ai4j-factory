package org.ai4j.factory.bi.semantic;

import java.util.List;

public class Subject {
    private String name;
    private String table;
    private String description;
    private List<Metric> metrics;
    private List<Dimension> dimensions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Metric> getMetrics() { return metrics; }
    public void setMetrics(List<Metric> metrics) { this.metrics = metrics; }
    public List<Dimension> getDimensions() { return dimensions; }
    public void setDimensions(List<Dimension> dimensions) { this.dimensions = dimensions; }
}
