package org.ai4j.factory.bi.intent;

import org.ai4j.factory.bi.clarification.PendingClarification;
import org.ai4j.factory.bi.semantic.Dimension;
import org.ai4j.factory.bi.semantic.Metric;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.chat.ChatClientFactory;
import org.ai4j.factory.sse.TraceEvent;
import org.ai4j.factory.sse.TraceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class IntentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(IntentExtractionService.class);
    private static final int MAX_RETRIES = 2;
    private static final String SPAN_ID = "intent-extraction";
    private static final String RETRY_FEEDBACK = "输出未通过解析或校验，请严格修正";

    private final ChatClientFactory chatClientFactory;
    private final SemanticLayer semanticLayer;
    private final BeanOutputConverter<IntentExtractionPayload> outputConverter;

    public IntentExtractionService(ChatClientFactory chatClientFactory, SemanticLayer semanticLayer) {
        this.chatClientFactory = chatClientFactory;
        this.semanticLayer = semanticLayer;
        this.outputConverter = new BeanOutputConverter<>(IntentExtractionPayload.class);
    }

    public IntentExtractionResult extract(String question, Long credentialId, String modelName) {
        return extractWithContext(question, null, credentialId, modelName);
    }

    public IntentExtractionResult extractWithContext(String question, PendingClarification context,
                                                     Long credentialId, String modelName) {
        return extractWithContext(question, context, credentialId, modelName, null);
    }

    public IntentExtractionResult extractWithContext(String question, PendingClarification context,
                                                     Long credentialId, String modelName,
                                                     Consumer<TraceEvent> traceEmitter) {
        Consumer<TraceEvent> emitter = traceEmitter == null ? e -> {} : traceEmitter;
        var chatClient = chatClientFactory.create(credentialId, modelName);
        String lastResponse = null;

        emitter.accept(new TraceEvent(
                SPAN_ID, null, SPAN_ID, TraceStatus.START, null));

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            String attemptLabel = String.valueOf(attempt + 1);
            String childSpanId = "llm-call-" + attemptLabel;
            Map<String, Object> startAttrs = new LinkedHashMap<>();
            startAttrs.put("attempt", attempt + 1);
            if (attempt > 0) {
                startAttrs.put("feedback", RETRY_FEEDBACK);
            }
            emitter.accept(new TraceEvent(
                    childSpanId, SPAN_ID, "llm-call", TraceStatus.START, startAttrs));

            String response = callLlm(chatClient, buildSystemPrompt(context),
                    buildUserPrompt(question, lastResponse, attempt == 0 ? null : RETRY_FEEDBACK));
            lastResponse = response;

            Map<String, Object> endAttrs = new LinkedHashMap<>();
            endAttrs.put("attempt", attempt + 1);
            endAttrs.put("rawOutput", response);

            try {
                IntentExtractionResult result = parseResponse(response);
                validate(result);
                IntentExtractionResult coerced = coerceEmptyMetrics(result);
                emitter.accept(new TraceEvent(
                        childSpanId, SPAN_ID, "llm-call", TraceStatus.END, endAttrs));
                emitter.accept(new TraceEvent(
                        SPAN_ID, null, SPAN_ID, TraceStatus.END, null));
                return coerced;
            } catch (Exception e) {
                endAttrs.put("error", e.getMessage());
                emitter.accept(new TraceEvent(
                        childSpanId, SPAN_ID, "llm-call", TraceStatus.END, endAttrs));
                log.warn("Intent extraction attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        emitter.accept(new TraceEvent(
                SPAN_ID, null, SPAN_ID, TraceStatus.END, null));
        throw new RuntimeException("Intent extraction failed after " + (MAX_RETRIES + 1)
                + " attempts. Last response: " + lastResponse);
    }

    private String buildSystemPrompt(PendingClarification context) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是一个 BI 查询意图识别助手。请根据用户的自然语言问题，输出结构化查询意图。

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
                    .map(option -> option.label() + "(" + option.value() + ")")
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
                8. 只输出符合格式的 JSON，不要补充解释
                9. 如果用户只是在回答上一次澄清问题，请结合上下文补全缺失信息

                ## 输出格式
                """);
        sb.append(outputConverter.getFormat());
        return sb.toString();
    }

    private String buildUserPrompt(String question, String previousResponse, String error) {
        StringBuilder sb = new StringBuilder();
        if (previousResponse != null && error != null) {
            sb.append("""
                    ## 修正要求
                    上一次输出存在问题：""");
            sb.append(error).append("\n");
            sb.append("上一次输出内容：\n").append(previousResponse).append("\n\n");
            sb.append("请修正后重新输出。\n\n");
        }

        sb.append("""
                ## 用户问题
                """);
        sb.append(question);
        return sb.toString();
    }

    IntentExtractionResult parseResponse(String response) {
        String json = extractJson(response);
        IntentExtractionPayload payload = outputConverter.convert(json);
        return payload.toResult();
    }

    String callLlm(org.springframework.ai.chat.client.ChatClient chatClient, String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
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
                    .map(Metric::getName)
                    .collect(Collectors.toSet());
            for (String metric : ready.metrics()) {
                if (!metricNames.contains(metric)) {
                    throw new IllegalArgumentException(
                            "Unknown metric '" + metric + "'. Available: " + metricNames);
                }
            }

            Set<String> dimensionNames = subject.getDimensions().stream()
                    .map(Dimension::getName)
                    .collect(Collectors.toSet());
            for (String dimension : ready.dimensions()) {
                if (!dimensionNames.contains(dimension)) {
                    throw new IllegalArgumentException(
                            "Unknown dimension '" + dimension + "'. Available: " + dimensionNames);
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

    private record IntentExtractionPayload(
            String status,
            String reason,
            String message,
            String subject,
            String metric,
            List<String> metrics,
            List<String> dimensions,
            List<Filter> filters
    ) {
        IntentExtractionResult toResult() {
            if ("needs_clarification".equals(status)) {
                return new IntentExtractionResult.NeedsClarification(reason, message, subject, metric);
            }
            return new IntentExtractionResult.Ready(subject, metrics, dimensions, filters);
        }
    }
}
