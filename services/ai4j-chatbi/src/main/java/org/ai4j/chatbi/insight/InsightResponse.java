package org.ai4j.chatbi.insight;

import java.util.List;
import java.util.Map;

public class InsightResponse {
    private String question;
    private String summary;
    private List<Map<String, Object>> data;
    private String chartType;

    public InsightResponse(String question, String summary, List<Map<String, Object>> data, String chartType) {
        this.question = question;
        this.summary = summary;
        this.data = data;
        this.chartType = chartType;
    }

    public String getQuestion() { return question; }
    public String getSummary() { return summary; }
    public List<Map<String, Object>> getData() { return data; }
    public String getChartType() { return chartType; }
}
