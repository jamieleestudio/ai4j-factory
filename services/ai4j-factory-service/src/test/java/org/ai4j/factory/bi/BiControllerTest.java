package org.ai4j.factory.bi;

import org.ai4j.factory.bi.clarification.ClarificationStore;
import org.ai4j.factory.bi.clarification.PendingClarification;
import org.ai4j.factory.bi.insight.InsightGenerationService;
import org.ai4j.factory.bi.insight.InsightStreamAssembler;
import org.ai4j.factory.bi.intent.IntentExtractionResult;
import org.ai4j.factory.bi.intent.IntentExtractionService;
import org.ai4j.factory.bi.query.QueryExecutionService;
import org.ai4j.factory.bi.query.SqlBuilder;
import org.ai4j.factory.bi.semantic.Dimension;
import org.ai4j.factory.bi.semantic.Metric;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.sse.DoneEvent;
import org.ai4j.factory.sse.SseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BiControllerTest {

    private IntentExtractionService intentService;
    private QueryExecutionService queryService;
    private InsightGenerationService insightService;
    private InsightStreamAssembler insightStreamAssembler;
    private QueryAssemblyService queryAssemblyService;
    private ClarificationService clarificationService;
    private SemanticLayer semanticLayer;
    private ClarificationStore clarificationStore;
    private BiQueryWorkflowService workflowService;
    private BiController controller;

    @BeforeEach
    void setUp() {
        intentService = mock(IntentExtractionService.class);
        queryService = mock(QueryExecutionService.class);
        insightService = mock(InsightGenerationService.class);
        insightStreamAssembler = mock(InsightStreamAssembler.class);
        queryAssemblyService = mock(QueryAssemblyService.class);
        clarificationService = mock(ClarificationService.class);
        semanticLayer = mock(SemanticLayer.class);
        clarificationStore = mock(ClarificationStore.class);

        workflowService = new BiQueryWorkflowService(
                intentService, queryService, insightService, insightStreamAssembler,
                queryAssemblyService, clarificationService, semanticLayer, true);
        controller = new BiController(workflowService);

        when(semanticLayer.toTracePayload()).thenReturn(List.of());
    }

    @Test
    void clarificationBranchPushesClarificationAndDoneOnly() {
        when(intentService.extractWithContext(eq("1"), any(), any(), any(), any()))
                .thenReturn(new IntentExtractionResult.NeedsClarification(
                        "question_unclear", "请选择主题", null, null));
        when(clarificationService.loadContext(any())).thenReturn(null);
        when(semanticLayer.getAllSubjects())
                .thenReturn((Collection<Subject>) List.of(createSubject("订单分析", "订单数据")));
        when(clarificationService.createEvent(any(), any(), any(), any()))
                .thenReturn(new org.ai4j.factory.sse.ClarificationEvent(
                        "session-1", "请选择主题", List.of()));

        List<ServerSentEvent<String>> events = controller.query("1", 1L, "model", null)
                .getBody().collectList().block();

        assertNotNull(events);
        String allEvents = events.stream()
                .map(ServerSentEvent::data)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(allEvents.contains("\"type\":\"clarification\""), "should push clarification event");
        assertTrue(allEvents.contains("\"type\":\"done\""), "should push done event");
        assertFalse(allEvents.contains("\"type\":\"intent\""), "should not push intent");
        assertFalse(allEvents.contains("\"type\":\"chunk\""), "should not push chunk");
        assertFalse(allEvents.contains("\"type\":\"result\""), "should not push result");
    }

    @Test
    void clarificationBranchStoresSessionId() {
        when(intentService.extractWithContext(eq("1"), any(), any(), any(), any()))
                .thenReturn(new IntentExtractionResult.NeedsClarification(
                        "question_unclear", "请选择主题", null, null));
        when(clarificationService.loadContext(any())).thenReturn(null);
        when(semanticLayer.getAllSubjects())
                .thenReturn((Collection<Subject>) List.of(createSubject("订单分析", "订单数据")));
        when(clarificationService.createEvent(any(), any(), any(), any()))
                .thenReturn(new org.ai4j.factory.sse.ClarificationEvent(
                        "session-1", "请选择主题", List.of()));

        controller.query("1", 1L, "model", null).getBody().collectList().block();

        verify(clarificationService).createEvent(any(), any(), any(), any());
    }

    @Test
    void sessionIdNotFoundFallsBackToFreshExtract() {
        when(clarificationService.loadContext(eq("unknown-session"))).thenReturn(null);
        when(intentService.extractWithContext(eq("question"), any(), any(), any(), any()))
                .thenReturn(new IntentExtractionResult.NeedsClarification(
                        "question_unclear", "请选择", null, null));
        when(semanticLayer.getAllSubjects())
                .thenReturn((Collection<Subject>) List.of(createSubject("订单分析", "订单数据")));
        when(clarificationService.createEvent(any(), any(), any(), any()))
                .thenReturn(new org.ai4j.factory.sse.ClarificationEvent(
                        "session-2", "请选择", List.of()));

        controller.query("question", 1L, "model", "unknown-session").getBody().collectList().block();

        verify(intentService).extractWithContext(eq("question"), any(), any(), any(), any());
    }

    @Test
    void sessionIdFoundCallsExtractWithContext() {
        PendingClarification context = new PendingClarification(
                "1", "question_unclear", List.of(), null, Instant.now());
        when(clarificationService.loadContext(eq("known-session"))).thenReturn(context);
        when(intentService.extractWithContext(eq("订单分析"), eq(context), any(), any(), any()))
                .thenReturn(new IntentExtractionResult.NeedsClarification(
                        "metric_unspecified", "请选择指标", "订单分析", null));
        Subject subject = createSubject("订单分析", "订单数据");
        when(semanticLayer.getSubject("订单分析")).thenReturn(subject);
        when(clarificationService.createEvent(any(), any(), any(), any()))
                .thenReturn(new org.ai4j.factory.sse.ClarificationEvent(
                        "session-3", "请选择指标", List.of()));

        controller.query("订单分析", 1L, "model", "known-session").getBody().collectList().block();

        verify(intentService).extractWithContext(eq("订单分析"), eq(context), any(), any(), any());
    }

    @Test
    void intentEventIncludesDimensionType() {
        IntentExtractionResult.Ready ready = new IntentExtractionResult.Ready(
                "订单分析", List.of("销售额"), List.of("区域"), List.of());
        when(intentService.extractWithContext(eq("华东区销售额"), any(), any(), any(), any()))
                .thenReturn(ready);
        when(clarificationService.loadContext(any())).thenReturn(null);

        Subject subject = createSubjectWithDimensionAndMetric();
        when(semanticLayer.getSubject("订单分析")).thenReturn(subject);
        BiQueryPlan plan = new BiQueryPlan(
                new org.ai4j.factory.bi.intent.QueryIntent(),
                subject,
                new SqlBuilder.SqlResult("SELECT 1", new Object[0]));
        when(queryAssemblyService.assemble(ready)).thenReturn(plan);
        when(queryAssemblyService.toIntentEvent(plan)).thenReturn(
                new org.ai4j.factory.sse.IntentEvent(
                        "订单分析",
                        List.of("销售额"),
                        List.of(new org.ai4j.factory.sse.DimensionRef("区域", "STRING")),
                        List.of()));
        when(queryService.execute(any(SqlBuilder.SqlResult.class)))
                .thenReturn(List.of(Map.of("区域", "华东", "销售额", 1000)));
        when(insightService.generateStream(any(), any(), any(), any()))
                .thenReturn(Flux.just("华东区销售额为 1000。"));
        when(insightStreamAssembler.assemble(any(), any()))
                .thenReturn(Flux.just(
                        new org.ai4j.factory.sse.ChunkEvent("华东区销售额为 1000。"),
                        new org.ai4j.factory.sse.ResultEvent("bar", List.of(), 1),
                        new DoneEvent()));

        List<ServerSentEvent<String>> events = controller.query("华东区销售额", 1L, "model", null)
                .getBody().collectList().block();

        assertNotNull(events);
        String intentEvent = events.stream()
                .map(ServerSentEvent::data)
                .filter(e -> e != null && e.contains("\"type\":\"intent\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("intent event not found"));
        assertTrue(intentEvent.contains("\"name\":\"区域\""), "intent event should include dimension name");
        assertTrue(intentEvent.contains("\"type\":\"STRING\""), "intent event should include dimension type");
    }

    @Test
    void streamEmitsTraceEventsInOrder() {
        IntentExtractionResult.Ready ready = new IntentExtractionResult.Ready(
                "订单分析", List.of("销售额"), List.of("区域"), List.of());
        when(intentService.extractWithContext(eq("华东区销售额"), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Consumer<org.ai4j.factory.sse.TraceEvent> emitter =
                            invocation.getArgument(4);
                    emitter.accept(new org.ai4j.factory.sse.TraceEvent(
                            "intent-extraction", null, "intent-extraction",
                            org.ai4j.factory.sse.TraceStatus.START, null));
                    emitter.accept(new org.ai4j.factory.sse.TraceEvent(
                            "llm-call-1", "intent-extraction", "llm-call",
                            org.ai4j.factory.sse.TraceStatus.START,
                            Map.of("attempt", 1)));
                    emitter.accept(new org.ai4j.factory.sse.TraceEvent(
                            "llm-call-1", "intent-extraction", "llm-call",
                            org.ai4j.factory.sse.TraceStatus.END,
                            Map.of("attempt", 1, "rawOutput", "{}")));
                    emitter.accept(new org.ai4j.factory.sse.TraceEvent(
                            "intent-extraction", null, "intent-extraction",
                            org.ai4j.factory.sse.TraceStatus.END, null));
                    return ready;
                });
        when(clarificationService.loadContext(any())).thenReturn(null);
        when(semanticLayer.toTracePayload()).thenReturn(List.of());

        Subject subject = createSubjectWithDimensionAndMetric();
        when(semanticLayer.getSubject("订单分析")).thenReturn(subject);
        BiQueryPlan plan = new BiQueryPlan(
                new org.ai4j.factory.bi.intent.QueryIntent(),
                subject,
                new SqlBuilder.SqlResult("SELECT 1", new Object[0]));
        when(queryAssemblyService.assemble(ready)).thenReturn(plan);
        when(queryAssemblyService.toIntentEvent(plan)).thenReturn(
                new org.ai4j.factory.sse.IntentEvent(
                        "订单分析",
                        List.of("销售额"),
                        List.of(new org.ai4j.factory.sse.DimensionRef("区域", "STRING")),
                        List.of()));
        when(queryService.execute(any(SqlBuilder.SqlResult.class)))
                .thenReturn(List.of(Map.of("区域", "华东", "销售额", 1000)));
        when(insightService.generateStream(any(), any(), any(), any()))
                .thenReturn(Flux.just("华东区销售额为 1000。"));
        when(insightStreamAssembler.assemble(any(), any()))
                .thenReturn(Flux.just(
                        new org.ai4j.factory.sse.ChunkEvent("华东区销售额为 1000。"),
                        new org.ai4j.factory.sse.ResultEvent("bar", List.of(), 1),
                        new DoneEvent()));

        List<ServerSentEvent<String>> events = controller.query("华东区销售额", 1L, "model", null)
                .getBody().collectList().block();

        assertNotNull(events);
        List<String> types = events.stream()
                .map(ServerSentEvent::data)
                .map(data -> {
                    int idx = data.indexOf("\"type\":\"");
                    if (idx < 0) return "unknown";
                    int start = idx + 8;
                    int end = data.indexOf("\"", start);
                    return data.substring(start, end);
                })
                .toList();

        // Verify trace events appear in expected order
        int semanticContextIdx = -1;
        int intentExtractionStartIdx = -1;
        int intentExtractionEndIdx = -1;
        int sqlBuildIdx = -1;
        int queryExecuteStartIdx = -1;
        int queryExecuteEndIdx = -1;
        int insightGenerationStartIdx = -1;
        int insightGenerationEndIdx = -1;
        int intentIdx = -1;
        int resultIdx = -1;

        for (int i = 0; i < types.size(); i++) {
            String data = events.get(i).data();
            if ("trace".equals(types.get(i))) {
                if (data.contains("\"name\":\"semantic-context\"") && data.contains("\"status\":\"END\"")) {
                    semanticContextIdx = i;
                } else if (data.contains("\"name\":\"intent-extraction\"") && data.contains("\"status\":\"START\"")) {
                    intentExtractionStartIdx = i;
                } else if (data.contains("\"name\":\"intent-extraction\"") && data.contains("\"status\":\"END\"")) {
                    intentExtractionEndIdx = i;
                } else if (data.contains("\"name\":\"sql-build\"")) {
                    sqlBuildIdx = i;
                } else if (data.contains("\"name\":\"query-execute\"") && data.contains("\"status\":\"START\"")) {
                    queryExecuteStartIdx = i;
                } else if (data.contains("\"name\":\"query-execute\"") && data.contains("\"status\":\"END\"")) {
                    queryExecuteEndIdx = i;
                    assertTrue(data.contains("\"rowCount\":1"), "query-execute END should carry rowCount");
                } else if (data.contains("\"name\":\"insight-generation\"") && data.contains("\"status\":\"START\"")) {
                    insightGenerationStartIdx = i;
                } else if (data.contains("\"name\":\"insight-generation\"") && data.contains("\"status\":\"END\"")) {
                    insightGenerationEndIdx = i;
                    assertTrue(data.contains("\"chartType\":\"bar\""), "insight-generation END should carry chartType");
                    assertFalse(data.contains("rawOutput"), "insight-generation END should not contain rawOutput");
                }
            } else if ("intent".equals(types.get(i))) {
                intentIdx = i;
            } else if ("result".equals(types.get(i))) {
                resultIdx = i;
            }
        }

        assertTrue(semanticContextIdx >= 0, "semantic-context trace should be emitted");
        assertTrue(intentExtractionStartIdx >= 0, "intent-extraction START should be emitted");
        assertTrue(intentExtractionEndIdx >= 0, "intent-extraction END should be emitted");
        assertTrue(sqlBuildIdx >= 0, "sql-build END should be emitted");
        assertTrue(queryExecuteStartIdx >= 0, "query-execute START should be emitted");
        assertTrue(queryExecuteEndIdx >= 0, "query-execute END should be emitted");
        assertTrue(insightGenerationStartIdx >= 0, "insight-generation START should be emitted");
        assertTrue(insightGenerationEndIdx >= 0, "insight-generation END should be emitted");

        // Verify chronological order
        assertTrue(semanticContextIdx < intentExtractionStartIdx,
                "semantic-context should come before intent-extraction START");
        assertTrue(intentExtractionStartIdx < intentExtractionEndIdx,
                "intent-extraction START should come before END");
        assertTrue(intentExtractionEndIdx < sqlBuildIdx,
                "intent-extraction END should come before sql-build END");
        assertTrue(sqlBuildIdx < intentIdx, "sql-build END should come before intent event");
        assertTrue(intentIdx < queryExecuteStartIdx, "intent event should come before query-execute START");
        assertTrue(queryExecuteStartIdx < queryExecuteEndIdx,
                "query-execute START should come before END");
        assertTrue(queryExecuteEndIdx < insightGenerationStartIdx,
                "query-execute END should come before insight-generation START");
        assertTrue(insightGenerationStartIdx < insightGenerationEndIdx,
                "insight-generation START should come before END");
        assertTrue(insightGenerationEndIdx < resultIdx,
                "insight-generation END should come before result event");
    }

    private Subject createSubject(String name, String description) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setDescription(description);
        return subject;
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
