package org.ai4j.factory.bi.intent;

import org.ai4j.factory.bi.semantic.Metric;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.chat.ChatClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentExtractionServiceTest {

    private ChatClientFactory chatClientFactory;
    private SemanticLayer semanticLayer;
    private IntentExtractionService service;

    @BeforeEach
    void setUp() {
        chatClientFactory = mock(ChatClientFactory.class);
        semanticLayer = mock(SemanticLayer.class);
        service = new IntentExtractionService(chatClientFactory, semanticLayer);
    }

    @Test
    void parseResponseParsesReadyStatus() {
        String json = """
                {"status":"ready", "subject":"订单分析", "metrics":["销售额"], "dimensions":["区域"], "filters":[]}
                """;
        IntentExtractionResult result = service.parseResponse(json);

        assertInstanceOf(IntentExtractionResult.Ready.class, result);
        IntentExtractionResult.Ready ready = (IntentExtractionResult.Ready) result;
        assertEquals("订单分析", ready.subject());
        assertEquals(List.of("销售额"), ready.metrics());
        assertEquals(List.of("区域"), ready.dimensions());
        assertTrue(ready.filters().isEmpty());
    }

    @Test
    void parseResponseParsesNeedsClarificationQuestionUnclear() {
        String json = """
                {"status":"needs_clarification", "reason":"question_unclear", "message":"请选择主题", "subject":null, "metric":null}
                """;
        IntentExtractionResult result = service.parseResponse(json);

        assertInstanceOf(IntentExtractionResult.NeedsClarification.class, result);
        IntentExtractionResult.NeedsClarification nc = (IntentExtractionResult.NeedsClarification) result;
        assertEquals("question_unclear", nc.reason());
        assertEquals("请选择主题", nc.message());
        assertNull(nc.subject());
        assertNull(nc.metric());
    }

    @Test
    void parseResponseParsesNeedsClarificationSubjectAmbiguous() {
        String json = """
                {"status":"needs_clarification", "reason":"subject_ambiguous", "message":"请选择主题", "subject":null, "metric":"销售额"}
                """;
        IntentExtractionResult result = service.parseResponse(json);

        assertInstanceOf(IntentExtractionResult.NeedsClarification.class, result);
        IntentExtractionResult.NeedsClarification nc = (IntentExtractionResult.NeedsClarification) result;
        assertEquals("subject_ambiguous", nc.reason());
        assertEquals("销售额", nc.metric());
    }

    @Test
    void parseResponseParsesNeedsClarificationMetricUnspecified() {
        String json = """
                {"status":"needs_clarification", "reason":"metric_unspecified", "message":"请选择指标", "subject":"订单分析", "metric":null}
                """;
        IntentExtractionResult result = service.parseResponse(json);

        assertInstanceOf(IntentExtractionResult.NeedsClarification.class, result);
        IntentExtractionResult.NeedsClarification nc = (IntentExtractionResult.NeedsClarification) result;
        assertEquals("metric_unspecified", nc.reason());
        assertEquals("订单分析", nc.subject());
    }

    @Test
    void parseResponseHandlesCodeFencedJson() {
        String response = """
                ```json
                {"status":"ready", "subject":"订单分析", "metrics":["销售额"], "dimensions":[], "filters":[]}
                ```
                """;
        IntentExtractionResult result = service.parseResponse(response);

        assertInstanceOf(IntentExtractionResult.Ready.class, result);
    }

    @Test
    void coerceEmptyMetricsConvertsReadyWithEmptyMetricsToClarification() {
        Subject subject = new Subject();
        subject.setMetrics(List.of(
                createMetric("销售额"), createMetric("订单量")
        ));
        when(semanticLayer.getSubject("订单分析")).thenReturn(subject);

        IntentExtractionResult.Ready ready = new IntentExtractionResult.Ready(
                "订单分析", List.of(), List.of(), List.of()
        );
        IntentExtractionResult result = service.coerceEmptyMetrics(ready);

        assertInstanceOf(IntentExtractionResult.NeedsClarification.class, result);
        IntentExtractionResult.NeedsClarification nc = (IntentExtractionResult.NeedsClarification) result;
        assertEquals("metric_unspecified", nc.reason());
        assertEquals("订单分析", nc.subject());
    }

    @Test
    void coerceEmptyMetricsPreservesReadyWithNonEmptyMetrics() {
        IntentExtractionResult.Ready ready = new IntentExtractionResult.Ready(
                "订单分析", List.of("销售额"), List.of(), List.of()
        );
        IntentExtractionResult result = service.coerceEmptyMetrics(ready);

        assertSame(ready, result);
    }

    @Test
    void coerceEmptyMetricsPreservesNeedsClarification() {
        IntentExtractionResult.NeedsClarification nc = new IntentExtractionResult.NeedsClarification(
                "question_unclear", "请选择", null, null
        );
        IntentExtractionResult result = service.coerceEmptyMetrics(nc);

        assertSame(nc, result);
    }

    private Metric createMetric(String name) {
        Metric metric = new Metric();
        metric.setName(name);
        return metric;
    }
}
