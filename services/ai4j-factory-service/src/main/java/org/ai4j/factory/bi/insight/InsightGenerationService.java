package org.ai4j.factory.bi.insight;

import org.ai4j.factory.chat.ChatClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
public class InsightGenerationService {

    private static final Logger log = LoggerFactory.getLogger(InsightGenerationService.class);
    private static final String CHART_MARKER = "<<CHART:";

    private final ChatClientFactory chatClientFactory;

    public InsightGenerationService(ChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    public InsightResponse generate(String question, List<Map<String, Object>> data,
                                     Long credentialId, String modelName) {
        if (data == null || data.isEmpty()) {
            return new InsightResponse(question, "没有查询到符合条件的数据。", List.of(), "single_value");
        }

        var chatClient = chatClientFactory.create(credentialId, modelName);
        String prompt = buildInsightPrompt(question, data);
        String response = chatClient.prompt().user(prompt).call().content();
        return parseInsightResponse(question, data, response);
    }

    public Flux<String> generateStream(String question, List<Map<String, Object>> data,
                                        Long credentialId, String modelName) {
        if (data == null || data.isEmpty()) {
            return Flux.just("没有查询到符合条件的数据。", CHART_MARKER + "single_value>>");
        }

        var chatClient = chatClientFactory.create(credentialId, modelName);
        String prompt = buildStreamingPrompt(question, data);
        return chatClient.prompt().user(prompt).stream().content();
    }

    public String extractChartType(String fullText) {
        int markerStart = fullText.lastIndexOf(CHART_MARKER);
        if (markerStart >= 0) {
            int valueStart = markerStart + CHART_MARKER.length();
            int valueEnd = fullText.indexOf(">>", valueStart);
            if (valueEnd > valueStart) {
                return fullText.substring(valueStart, valueEnd).trim();
            }
        }
        return "bar";
    }

    public int safeDisplayLength(String fullText) {
        int markerStart = fullText.lastIndexOf(CHART_MARKER);
        if (markerStart >= 0) {
            return markerStart;
        }
        int holdback = partialMarkerPrefixLength(fullText);
        return fullText.length() - holdback;
    }

    private int partialMarkerPrefixLength(String fullText) {
        int max = Math.min(fullText.length(), CHART_MARKER.length());
        for (int len = max; len >= 1; len--) {
            String suffix = fullText.substring(fullText.length() - len);
            if (CHART_MARKER.startsWith(suffix)) {
                return len;
            }
        }
        return 0;
    }

    private String buildStreamingPrompt(String question, List<Map<String, Object>> data) {
        return buildInsightPrompt(question, data) + """

                ## 输出格式
                直接输出自然语言总结，不要JSON格式。在最后单独一行输出图表类型标记: <<CHART:图表类型>>
                """;
    }

    private String buildInsightPrompt(String question, List<Map<String, Object>> data) {
        StringBuilder dataStr = new StringBuilder();
        if (!data.isEmpty()) {
            List<String> columns = data.get(0).keySet().stream().toList();
            dataStr.append("| ").append(String.join(" | ", columns)).append(" |\n");
            dataStr.append("|").append("---|".repeat(columns.size())).append("\n");
            for (Map<String, Object> row : data) {
                dataStr.append("| ");
                for (String col : columns) {
                    Object val = row.get(col);
                    dataStr.append(val != null ? val.toString() : "-").append(" | ");
                }
                dataStr.append("\n");
            }
        }

        return """
                你是一个数据分析助手。根据用户的原始问题和查询结果，生成洞察。

                ## 用户问题
                %s

                ## 查询结果
                %s

                ## 任务
                1. 用自然语言总结数据，突出关键发现
                2. 推荐一个最适合的图表类型

                ## 图表类型（按数据形状选择）
                - single_value: 单个数值。适用：结果仅 1 行 1 列（无维度，仅 1 个指标）
                - bar: 柱状图。适用：1 个分类维度 + 1 个指标，用于分类对比
                - pie: 饼图。适用：1 个分类维度 + 1 个指标，用于展示占比。仅在维度数 = 1 时使用
                - line: 折线图。适用：1 个时间维度 + 1 个指标，用于趋势
                - grouped_bar: 分组柱状图。适用：2 个维度 + 1 个指标，用于并列对比
                - stacked_bar: 堆叠柱状图。适用：2 个维度 + 1 个指标，用于堆叠占比
                - heatmap: 热力图。适用：2 个维度 + 1 个指标，用于密度分布
                - line_multi: 多线折线图。适用：1 个时间维度 + 1 个分组维度 + 1 个指标，用于多序列趋势

                ## 选择约束
                - 维度数 = 0：single_value
                - 维度数 = 1：按维度是否为时间序列选择 line，否则 bar 或 pie
                - 维度数 = 2：grouped_bar / stacked_bar / heatmap（含时间维度时优先 line_multi）
                - 维度数 ≥ 2 时不要选 pie
                """.formatted(question, dataStr.toString());
    }

    private InsightResponse parseInsightResponse(String question, List<Map<String, Object>> data, String response) {
        try {
            String json = response.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf("\n");
                int end = json.lastIndexOf("```");
                if (start >= 0 && end > start) {
                    json = json.substring(start, end).trim();
                }
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            String summary = node.has("summary") ? node.get("summary").asText() : "查询完成";
            String chartType = node.has("chartType") ? node.get("chartType").asText() : "single_value";
            return new InsightResponse(question, summary, data, chartType);
        } catch (Exception e) {
            log.warn("Failed to parse insight response, using defaults: {}", e.getMessage());
            return new InsightResponse(question, "查询完成，共返回 " + data.size() + " 条记录。", data, "single_value");
        }
    }
}
