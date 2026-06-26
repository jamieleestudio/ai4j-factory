package org.ai4j.chatbi.intent;

import java.util.ArrayList;
import java.util.List;

public class QueryIntent {
    private String subject;
    private List<String> metrics = new ArrayList<>();
    private List<String> dimensions = new ArrayList<>();
    private List<Filter> filters = new ArrayList<>();

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public List<String> getMetrics() { return metrics; }
    public void setMetrics(List<String> metrics) { this.metrics = metrics; }

    public List<String> getDimensions() { return dimensions; }
    public void setDimensions(List<String> dimensions) { this.dimensions = dimensions; }

    public List<Filter> getFilters() { return filters; }
    public void setFilters(List<Filter> filters) { this.filters = filters; }
}
