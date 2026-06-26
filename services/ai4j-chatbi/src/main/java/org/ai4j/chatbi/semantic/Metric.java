package org.ai4j.chatbi.semantic;

public class Metric {
    private String name;
    private String column;
    private Aggregation aggregation;
    private String description;

    public enum Aggregation {
        SUM, COUNT, AVG, MAX, MIN
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public Aggregation getAggregation() { return aggregation; }
    public void setAggregation(Aggregation aggregation) { this.aggregation = aggregation; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
