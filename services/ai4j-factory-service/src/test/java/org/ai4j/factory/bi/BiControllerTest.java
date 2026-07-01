package org.ai4j.factory.bi;

import org.ai4j.factory.bi.clarification.ClarificationStore;
import org.ai4j.factory.bi.clarification.PendingClarification;
import org.ai4j.factory.bi.insight.InsightGenerationService;
import org.ai4j.factory.bi.intent.IntentExtractionResult;
import org.ai4j.factory.bi.intent.IntentExtractionService;
import org.ai4j.factory.bi.query.QueryExecutionService;
import org.ai4j.factory.bi.query.SqlBuilder;
import org.ai4j.factory.bi.semantic.Dimension;
import org.ai4j.factory.bi.semantic.Metric;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BiControllerTest {

    private IntentExtractionService intentService;
    private QueryExecutionService queryService;
    private InsightGenerationService insightService;
    private SemanticLayer semanticLayer;
    private ClarificationStore clarificationStore;
    private BiController controller;

    @BeforeEach
    void setUp() {
        intentService = mock(IntentExtractionService.class);
        queryService = mock(QueryExecutionService.class);
        insightService = mock(InsightGenerationService.class);
        semanticLayer = mock(SemanticLayer.class);
        clarificationStore = mock(ClarificationStore.class);
        controller = new BiController(intentService, queryService, insightService, semanticLayer, clarificationStore);
    }

    @Test
    void clarificationBranchPushesClarificationAndDoneOnly() {
        when(intentService.extract(eq("1"), any(), any()))
                .thenReturn(new IntentExtractionResult.NeedsClarification(
                        "question_unclear", "请选择主题", null, null));
        when(semanticLayer.getAllSubjects())
                .thenReturn((Collection<Subject>) List.of(createSubject("订单分析", "订单数据")));

        BiController.QueryRequest request = new BiController.QueryRequest("1", 1L, "model", null);
        List<String> events = controller.query(request).collectList().block();

        assertNotNull(events);
        String allEvents = String.join("\n", events);
        assertTrue(allEvents.contains("\"type\":\"clarification\""), "should push clarification event");
        assertTrue(allEvents.contains("\"type\":\"done\""), "should push done event");
        assertFalse(allEvents.contains("\"type\":\"intent\""), "should not push intent");
        assertFalse(allEvents.contains("\"type\":\"chunk\""), "should not push chunk");
        assertFalse(allEvents.contains("\"type\":\"result\""), "should not push result");
    }

    @Test
    void clarificationBranchStoresSessionId() {
        when(intentService.extract(eq("1"), any(), any()))
                .thenReturn(new IntentExtractionResult.NeedsClarification(
                        "question_unclear", "请选择主题", null, null));
        when(semanticLayer.getAllSubjects())
                .thenReturn((Collection<Subject>) List.of(createSubject("订单分析", "订单数据")));

        BiController.QueryRequest request = new BiController.QueryRequest("1", 1L, "model", null);
        controller.query(request).collectList().block();

        verify(clarificationStore).put(any(String.class), any(PendingClarification.class));
    }

    @Test
    void sessionIdNotFoundFallsBackToFreshExtract() {
        when(clarificationStore.get("unknown-session")).thenReturn(Optional.empty());
        when(intentService.extract(eq("question"), any(), any()))
                .thenReturn(new IntentExtractionResult.NeedsClarification(
                        "question_unclear", "请选择", null, null));
        when(semanticLayer.getAllSubjects())
                .thenReturn((Collection<Subject>) List.of(createSubject("订单分析", "订单数据")));

        BiController.QueryRequest request = new BiController.QueryRequest("question", 1L, "model", "unknown-session");
        controller.query(request).collectList().block();

        verify(intentService).extract(eq("question"), any(), any());
        verify(intentService, never()).extractWithContext(any(), any(), any(), any());
    }

    @Test
    void sessionIdFoundCallsExtractWithContext() {
        PendingClarification context = new PendingClarification(
                "1", "question_unclear", List.of(), null, Instant.now());
        when(clarificationStore.get("known-session")).thenReturn(Optional.of(context));
        when(intentService.extractWithContext(eq("订单分析"), eq(context), any(), any()))
                .thenReturn(new IntentExtractionResult.NeedsClarification(
                        "metric_unspecified", "请选择指标", "订单分析", null));
        Subject subject = createSubject("订单分析", "订单数据");
        when(semanticLayer.getSubject("订单分析")).thenReturn(subject);

        BiController.QueryRequest request = new BiController.QueryRequest("订单分析", 1L, "model", "known-session");
        controller.query(request).collectList().block();

        verify(intentService).extractWithContext(eq("订单分析"), eq(context), any(), any());
        verify(intentService, never()).extract(any(), any(), any());
    }

    private Subject createSubject(String name, String description) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setDescription(description);
        return subject;
    }

    @Test
    void intentEventIncludesDimensionType() {
        when(intentService.extract(eq("华东区销售额"), any(), any()))
                .thenReturn(new IntentExtractionResult.Ready(
                        "订单分析",
                        List.of("销售额"),
                        List.of("区域"),
                        List.of()));

        Subject subject = createSubjectWithDimensionAndMetric();
        when(semanticLayer.getSubject("订单分析")).thenReturn(subject);
        when(queryService.execute(any(SqlBuilder.SqlResult.class)))
                .thenReturn(List.of(Map.of("区域", "华东", "销售额", 1000)));
        when(insightService.generateStream(any(), any(), any(), any()))
                .thenReturn(Flux.just("华东区销售额为 1000。"));
        when(insightService.safeDisplayLength(any())).thenAnswer(inv -> ((String) inv.getArgument(0)).length());
        when(insightService.extractChartType(any())).thenReturn("bar");

        BiController.QueryRequest request = new BiController.QueryRequest("华东区销售额", 1L, "model", null);
        List<String> events = controller.query(request).collectList().block();

        assertNotNull(events);
        String intentEvent = events.stream()
                .filter(e -> e.contains("\"type\":\"intent\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("intent event not found"));
        assertTrue(intentEvent.contains("\"name\":\"区域\""), "intent event should include dimension name");
        assertTrue(intentEvent.contains("\"type\":\"STRING\""), "intent event should include dimension type");
    }

    private Subject createSubjectWithDimensionAndMetric() {
        Subject subject = new Subject();
        subject.setName("订单分析");
        subject.setTable("orders");
        subject.setDescription("订单数据");

        Dimension dim = new Dimension();
        dim.setName("区域");
        dim.setColumn("region");
        dim.setType(Dimension.DataType.STRING);
        subject.setDimensions(List.of(dim));

        Metric metric = new Metric();
        metric.setName("销售额");
        metric.setColumn("amount");
        metric.setAggregation(Metric.Aggregation.SUM);
        subject.setMetrics(List.of(metric));

        return subject;
    }
}
