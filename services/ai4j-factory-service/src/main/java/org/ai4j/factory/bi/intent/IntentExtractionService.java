package org.ai4j.factory.bi.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ai4j.factory.bi.clarification.PendingClarification;
import org.ai4j.factory.bi.semantic.Dimension;
import org.ai4j.factory.bi.semantic.Metric;
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

    public IntentExtractionResult extract(String question, Long credentialId, String modelName) {
        return extractWithContext(question, null, credentialId, modelName);
    }

    public IntentExtractionResult extractWithContext(String question, PendingClarification context,
                                                      Long credentialId, String modelName) {
        var chatClient = chatClientFactory.create(credentialId, modelName);
        String prompt = buildExtractionPrompt(question, context);
        String lastResponse = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            String response = chatClient.prompt().user(prompt).call().content();
            lastResponse = response;
            try {
                IntentExtractionResult result = parseResponse(response);
                validate(result);
                return coerceEmptyMetrics(result);
            } catch (Exception e) {
                log.warn("Intent extraction attempt {} failed: {}", attempt + 1, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    prompt = buildCorrectionPrompt(question, context, response, e.getMessage());
                }
            }
        }

        throw new RuntimeException("Intent extraction failed after " + (MAX_RETRIES + 1)
                + " attempts. Last response: " + lastResponse);
    }

    private String buildExtractionPrompt(String question, PendingClarification context) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是一个数据分析助手。根据用户的自然语言问题，提取结构化的查询意图。

                ## 可用的数据主题

                """);
        sb.append(semanticLayer.toPromptSummary());

        if (context != null) {
            sb.append("""

                    ## 上下文（多轮澄清）
                    用户之前问：""");
            sb.append(context.originalQuestion()).append("\n");
            sb.append("系统建议的选项：");
            String optionsText = context.options().stream()
                    .map(o -> o.label() + "(" + o.value() + ")")
                    .collect(Collectors.joining(", "));
            sb.append(optionsText).append("\n\n");
            sb.append("请基于上下文理解用户本次输入。如果用户的选择已经明确了主题和指标，输出 status=\"ready\"。\n\n");
        }

        sb.append("""

                ## 规则
                1. 只使用上面列出的主题、指标和维度名称
                2. 如果用户的问题明确指定了主题和至少一个指标（或能从上下文推断出指标），输出 status="ready"
                3. 如果用户的问题不明确，输出 status="needs_clarification"，并根据以下情况选择 reason：
                   - "question_unclear"：用户输入无法理解（如纯数字、乱码、无意义内容）
                   - "subject_ambiguous"：用户提到了指标但未指定主题（如"销售额"），此时 metric 字段填写用户提到的指标名
                   - "metric_unspecified"：用户指定了主题但未指定指标（如"订单分析"），此时 subject 字段填写用户提到的主题名
                4. 对于 needs_clarification，message 字段填写引导用户的提示文案
                5. 如果用户没有提到任何维度，dimensions 返回空数组
                6. 如果用户提到了过滤条件（如"华东区"、"电子产品"），放到 filters 数组中
                7. filters 中 operator 取值为 "=" 或 "!=" 或 ">" 或 "<"
                8. 只返回 JSON，不要其他文字

                ## 用户问题
                """);
        sb.append(question);

        sb.append("""

                ## 输出格式

                明确时：
                {"status":"ready", "subject":"主题名", "metrics":["指标1"], "dimensions":["维度1"], "filters":[{"dimension":"维度名", "operator":"=", "value":"值"}]}

                需要澄清时（question_unclear / subject_ambiguous）：
                {"status":"needs_clarification", "reason":"question_unclear", "message":"请选择您想分析的数据主题"}
                {"status":"needs_clarification", "reason":"subject_ambiguous", "message":"请选择数据主题", "metric":"销售额"}

                需要澄清时（metric_unspecified）：
                {"status":"needs_clarification", "reason":"metric_unspecified", "message":"请选择您想查看的指标", "subject":"订单分析"}
                """);
        return sb.toString();
    }

    private String buildCorrectionPrompt(String question, PendingClarification context,
                                          String previousResponse, String error) {
        return """
                你上一次的回答有错误：%s

                请修正后重新输出。确保只使用可用的主题、指标和维度名称。

                ## 用户问题
                %s

                ## 输出格式
                {"status":"ready", "subject":"主题名", "metrics":["指标1"], "dimensions":["维度1"], "filters":[{"dimension":"维度名", "operator":"=", "value":"值"}]}

                或：
                {"status":"needs_clarification", "reason":"question_unclear"|"subject_ambiguous"|"metric_unspecified", "message":"...", "subject":"可选", "metric":"可选"}
                """.formatted(error, question);
    }

    IntentExtractionResult parseResponse(String response) {
        try {
            String json = extractJson(response);
            return objectMapper.readValue(json, IntentExtractionResult.class);
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

    void validate(IntentExtractionResult result) {
        if (result instanceof IntentExtractionResult.Ready ready) {
            Subject subject = semanticLayer.getSubject(ready.subject());

            Set<String> metricNames = subject.getMetrics().stream()
                    .map(Metric::getName).collect(Collectors.toSet());
            for (String metric : ready.metrics()) {
                if (!metricNames.contains(metric)) {
                    throw new IllegalArgumentException(
                            "Unknown metric '" + metric + "'. Available: " + metricNames);
                }
            }

            Set<String> dimensionNames = subject.getDimensions().stream()
                    .map(Dimension::getName).collect(Collectors.toSet());
            for (String dim : ready.dimensions()) {
                if (!dimensionNames.contains(dim)) {
                    throw new IllegalArgumentException(
                            "Unknown dimension '" + dim + "'. Available: " + dimensionNames);
                }
            }

            for (Filter filter : ready.filters()) {
                if (!dimensionNames.contains(filter.getDimension())) {
                    throw new IllegalArgumentException(
                            "Unknown filter dimension '" + filter.getDimension() + "'");
                }
            }
        }
    }

    IntentExtractionResult coerceEmptyMetrics(IntentExtractionResult result) {
        if (result instanceof IntentExtractionResult.Ready ready && ready.metrics().isEmpty()) {
            Subject subject = semanticLayer.getSubject(ready.subject());
            if (!subject.getMetrics().isEmpty()) {
                return new IntentExtractionResult.NeedsClarification(
                        "metric_unspecified",
                        "请选择您想查看的指标",
                        ready.subject(),
                        null
                );
            }
        }
        return result;
    }
}
