package org.ai4j.factory.bi.semantic;

public class Dimension {
    private String name;
    private String column;
    private DataType type;

    public enum DataType {
        STRING, NUMBER, TIME
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }
    public DataType getType() { return type; }
    public void setType(DataType type) { this.type = type; }
}
