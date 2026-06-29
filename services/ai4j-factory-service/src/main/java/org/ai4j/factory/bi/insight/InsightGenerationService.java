package org.ai4j.factory.bi.insight;

import org.ai4j.factory.chat.ChatClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class InsightGenerationService {

    private static final Logger log = LoggerFactory.getLogger(InsightGenerationService.class);

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

                ## 图表类型
                - single_value: 单个数值
                - bar: 柱状图（分类对比）
                - line: 折线图（时间序列）
                - pie: 饼图（占比）

                ## 输出格式（只返回 JSON，不要其他文字）
                {"summary": "数据总结", "chartType": "bar"}
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
