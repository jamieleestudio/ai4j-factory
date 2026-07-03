package org.ai4j.factory.bi.intent;

import org.ai4j.factory.bi.semantic.Metric;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.chat.ChatClientFactory;
import org.ai4j.factory.sse.TraceEvent;
import org.ai4j.factory.sse.TraceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    private IntentExtractionService spyWithLlmResponses(String... responses) {
        IntentExtractionService spy = spy(service);
        if (responses.length == 1) {
            doReturn(responses[0]).when(spy).callLlm(any(), anyString(), anyString());
        } else {
            // First response, then subsequent responses
            doReturn(responses[0]).when(spy).callLlm(any(), anyString(), anyString());
            for (int i = 1; i < responses.length; i++) {
                doReturn(responses[i]).when(spy)
                        .callLlm(any(), anyString(), contains("上一次输出存在问题"));
            }
        }
        return spy;
    }

    @Test
    void traceEventsForSingleSuccess() {
        IntentExtractionService spy = spyWithLlmResponses("""
                {"status":"ready", "subject":"订单分析", "metrics":["销售额"], "dimensions":[], "filters":[]}
                """);
        when(semanticLayer.getSubject("订单分析")).thenReturn(createSubjectWithMetric("销售额"));

        List<TraceEvent> traces = new ArrayList<>();
        spy.extractWithContext("question", null, 1L, "model", traces::add);

        // Expected: START intent-extraction, START llm-call-1, END llm-call-1, END intent-extraction
        assertEquals(4, traces.size());
        assertTrace(traces.get(0), "intent-extraction", null, "intent-extraction", TraceStatus.START, null);
        assertTrace(traces.get(1), "llm-call-1", "intent-extraction", "llm-call", TraceStatus.START, "attempt", 1);
        assertTrace(traces.get(2), "llm-call-1", "intent-extraction", "llm-call", TraceStatus.END, "rawOutput");
        assertTrace(traces.get(3), "intent-extraction", null, "intent-extraction", TraceStatus.END, null);
        // No error in successful llm-call END
        assertNull(traces.get(2).attributes().get("error"));
        assertNull(traces.get(1).attributes().get("feedback"));
    }

    @Test
    void traceEventsForRetryThenSuccess() {
        IntentExtractionService spy = spyWithLlmResponses(
                // First attempt: invalid metric
                """
                {"status":"ready", "subject":"订单分析", "metrics":["nonexistent"], "dimensions":[], "filters":[]}
                """,
                // Second attempt: valid
                """
                {"status":"ready", "subject":"订单分析", "metrics":["销售额"], "dimensions":[], "filters":[]}
                """
        );
        when(semanticLayer.getSubject("订单分析")).thenReturn(createSubjectWithMetric("销售额"));

        List<TraceEvent> traces = new ArrayList<>();
        spy.extractWithContext("question", null, 1L, "model", traces::add);

        // Expected: START intent-extraction, START llm-call-1, END llm-call-1 (error),
        // START llm-call-2 (feedback), END llm-call-2, END intent-extraction
        assertEquals(6, traces.size());
        assertTrace(traces.get(0), "intent-extraction", null, "intent-extraction", TraceStatus.START, null);
        assertTrace(traces.get(1), "llm-call-1", "intent-extraction", "llm-call", TraceStatus.START, "attempt", 1);
        assertTrace(traces.get(2), "llm-call-1", "intent-extraction", "llm-call", TraceStatus.END, "error");
        assertNotNull(traces.get(2).attributes().get("rawOutput"));
        assertTrace(traces.get(3), "llm-call-2", "intent-extraction", "llm-call", TraceStatus.START, "feedback");
        assertEquals(2, traces.get(3).attributes().get("attempt"));
        assertTrace(traces.get(4), "llm-call-2", "intent-extraction", "llm-call", TraceStatus.END, "rawOutput");
        assertNull(traces.get(4).attributes().get("error"));
        assertTrace(traces.get(5), "intent-extraction", null, "intent-extraction", TraceStatus.END, null);
    }

    @Test
    void traceEventsForAllAttemptsFail() {
        IntentExtractionService spy = spyWithLlmResponses(
                """
                {"status":"ready", "subject":"订单分析", "metrics":["nonexistent"], "dimensions":[], "filters":[]}
                """,
                """
                {"status":"ready", "subject":"订单分析", "metrics":["nonexistent"], "dimensions":[], "filters":[]}
                """,
                """
                {"status":"ready", "subject":"订单分析", "metrics":["nonexistent"], "dimensions":[], "filters":[]}
                """
        );
        when(semanticLayer.getSubject("订单分析")).thenReturn(createSubjectWithMetric("销售额"));

        List<TraceEvent> traces = new ArrayList<>();
        assertThrows(RuntimeException.class,
                () -> spy.extractWithContext("question", null, 1L, "model", traces::add));

        // Expected: START intent-extraction, START/END llm-call-1 (error), START/END llm-call-2 (error),
        // START/END llm-call-3 (error), END intent-extraction
        assertEquals(8, traces.size());
        assertTrace(traces.get(0), "intent-extraction", null, "intent-extraction", TraceStatus.START, null);
        for (int i = 0; i < 3; i++) {
            int startIdx = 1 + i * 2;
            int endIdx = startIdx + 1;
            assertTrace(traces.get(startIdx), "llm-call-" + (i + 1), "intent-extraction", "llm-call",
                    TraceStatus.START, "attempt");
            assertTrace(traces.get(endIdx), "llm-call-" + (i + 1), "intent-extraction", "llm-call",
                    TraceStatus.END, "error");
            if (i > 0) {
                assertNotNull(traces.get(startIdx).attributes().get("feedback"));
            }
        }
        assertTrace(traces.get(7), "intent-extraction", null, "intent-extraction", TraceStatus.END, null);
    }

    private Subject createSubjectWithMetric(String metricName) {
        Subject subject = new Subject();
        subject.setMetrics(List.of(createMetric(metricName)));
        subject.setDimensions(List.of());
        return subject;
    }

    private void assertTrace(TraceEvent event, String spanId, String parentId, String name,
                             TraceStatus status, String attributeKey) {
        assertEquals(spanId, event.spanId());
        assertEquals(parentId, event.parentId());
        assertEquals(name, event.name());
        assertEquals(status, event.status());
        if (attributeKey != null) {
            assertNotNull(event.attributes(), "attributes should be non-null for " + name + " " + status);
            assertNotNull(event.attributes().get(attributeKey),
                    "attributes should contain " + attributeKey + " for " + name + " " + status);
        }
    }

    private void assertTrace(TraceEvent event, String spanId, String parentId, String name,
                             TraceStatus status, String attributeKey, Object attributeValue) {
        assertTrace(event, spanId, parentId, name, status, attributeKey);
        assertEquals(attributeValue, event.attributes().get(attributeKey));
    }
}
