package org.ai4j.factory.bi.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.chat.ChatClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IntentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(IntentExtractionService.class);
    private static final int MAX_RETRIES = 2;

    private final ChatClientFactory chatClientFactory;
    private final SemanticLayer semanticLayer;
    private final ObjectMapper objectMapper;

    public IntentExtractionService(ChatClientFactory chatClientFactory, SemanticLayer semanticLayer) {
        this.chatClientFactory = chatClientFactory;
        this.semanticLayer = semanticLayer;
        this.objectMapper = new ObjectMapper();
    }

    public QueryIntent extract(String question, Long credentialId, String modelName) {
        var chatClient = chatClientFactory.create(credentialId, modelName);
        String prompt = buildExtractionPrompt(question);
        QueryIntent intent;
        String lastResponse = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            String response = chatClient.prompt().user(prompt).call().content();
            lastResponse = response;
            try {
                intent = parseResponse(response);
                validate(intent);
                return intent;
            } catch (Exception e) {
                log.warn("Intent extraction attempt {} failed: {}", attempt + 1, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    prompt = buildCorrectionPrompt(question, response, e.getMessage());
                }
            }
        }

        throw new RuntimeException("Intent extraction failed after " + (MAX_RETRIES + 1)
                + " attempts. Last response: " + lastResponse);
    }

    private String buildCorrectionPrompt(String question, String previousResponse, String error) {
        return """
                你上一次的回答有错误：%s

                请修正后重新输出。确保只使用可用的主题、指标和维度名称。

                ## 用户问题
                %s

                ## 输出格式
                {"subject": "主题名", "metrics": ["指标1"], "dimensions": ["维度1"], "filters": [{"dimension": "维度名", "operator": "=", "value": "值"}]}
                """.formatted(error, question);
    }

    private String buildExtractionPrompt(String question) {
        return """
                你是一个数据分析助手。根据用户的自然语言问题，提取结构化的查询意图。

                ## 可用的数据主题

                %s

                ## 规则
                1. 只使用上面列出的主题、指标和维度名称
                2. 指标名称和维度名称与上面列出的一模一样则直接查询，如果不确定需要将指标和维度给到用户让用户选择
                3. 如果用户的指标没有明确指定，列出指标让用户选择
                4. 如果用户没有提到任何维度，dimensions 返回空数组
                5. 如果用户提到了过滤条件（如"华东区"、"电子产品"），放到 filters 数组中
                6. filters 中 operator 取值为 "=" 或 "!=" 或 ">" 或 "<"
                7. 只返回 JSON，不要其他文字

                ## 用户问题
                %s

                ## 输出格式
                {"subject": "主题名", "metrics": ["指标1"], "dimensions": ["维度1"], "filters": [{"dimension": "维度名", "operator": "=", "value": "值"}]}
                """.formatted(semanticLayer.toPromptSummary(), question);
    }

    QueryIntent parseResponse(String response) {
        try {
            String json = extractJson(response);
            return objectMapper.readValue(json, QueryIntent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse LLM intent response: " + response, e);
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        return trimmed;
    }

    void validate(QueryIntent intent) {
        Subject subject = semanticLayer.getSubject(intent.getSubject());

        Set<String> metricNames = subject.getMetrics().stream()
                .map(m -> m.getName()).collect(Collectors.toSet());
        for (String metric : intent.getMetrics()) {
            if (!metricNames.contains(metric)) {
                throw new IllegalArgumentException(
                        "Unknown metric '" + metric + "'. Available: " + metricNames);
            }
        }

        Set<String> dimensionNames = subject.getDimensions().stream()
                .map(d -> d.getName()).collect(Collectors.toSet());
        for (String dim : intent.getDimensions()) {
            if (!dimensionNames.contains(dim)) {
                throw new IllegalArgumentException(
                        "Unknown dimension '" + dim + "'. Available: " + dimensionNames);
            }
        }

        if (intent.getFilters() != null) {
            for (Filter filter : intent.getFilters()) {
                if (!dimensionNames.contains(filter.getDimension())) {
                    throw new IllegalArgumentException(
                            "Unknown filter dimension '" + filter.getDimension() + "'");
                }
            }
        }

        if (intent.getMetrics().isEmpty() && !subject.getMetrics().isEmpty()) {
            intent.getMetrics().add(subject.getMetrics().get(0).getName());
        }
    }
}
