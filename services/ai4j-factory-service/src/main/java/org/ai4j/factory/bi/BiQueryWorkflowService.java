package org.ai4j.factory.bi;

import org.ai4j.factory.bi.clarification.PendingClarification;
import org.ai4j.factory.bi.insight.InsightGenerationService;
import org.ai4j.factory.bi.insight.InsightStreamAssembler;
import org.ai4j.factory.bi.intent.IntentExtractionResult;
import org.ai4j.factory.bi.intent.IntentExtractionService;
import org.ai4j.factory.bi.query.QueryExecutionService;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.sse.DoneEvent;
import org.ai4j.factory.sse.ErrorEvent;
import org.ai4j.factory.sse.ResultEvent;
import org.ai4j.factory.sse.SseEvent;
import org.ai4j.factory.sse.StatusEvent;
import org.ai4j.factory.sse.TraceEvent;
import org.ai4j.factory.sse.TraceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class BiQueryWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(BiQueryWorkflowService.class);

    private final IntentExtractionService intentExtractionService;
    private final QueryExecutionService queryExecutionService;
    private final InsightGenerationService insightGenerationService;
    private final InsightStreamAssembler insightStreamAssembler;
    private final QueryAssemblyService queryAssemblyService;
    private final ClarificationService clarificationService;
    private final SemanticLayer semanticLayer;
    private final boolean traceEnabled;

    public BiQueryWorkflowService(IntentExtractionService intentExtractionService,
                                  QueryExecutionService queryExecutionService,
                                  InsightGenerationService insightGenerationService,
                                  InsightStreamAssembler insightStreamAssembler,
                                  QueryAssemblyService queryAssemblyService,
                                  ClarificationService clarificationService,
                                  SemanticLayer semanticLayer,
                                  @Value("${bi.trace.enabled:true}") boolean traceEnabled) {
        this.intentExtractionService = intentExtractionService;
        this.queryExecutionService = queryExecutionService;
        this.insightGenerationService = insightGenerationService;
        this.insightStreamAssembler = insightStreamAssembler;
        this.queryAssemblyService = queryAssemblyService;
        this.clarificationService = clarificationService;
        this.semanticLayer = semanticLayer;
        this.traceEnabled = traceEnabled;
    }

    public Flux<SseEvent> stream(BiQueryRequest request) {
        log.info("BI query: {}, sessionId: {}", request.question(), request.sessionId());

        return Flux.concat(
                Flux.just(new StatusEvent("analyzing", "正在分析你的问题...")),
                semanticContextTrace(),
                Mono.fromCallable(() -> resolveIntent(request))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(stage -> continueWorkflow(request, stage))
        ).onErrorResume(error -> {
            log.error("BI query failed", error);
            return Flux.just(new ErrorEvent(error.getMessage()), new DoneEvent());
        });
    }

    private Flux<SseEvent> semanticContextTrace() {
        if (!traceEnabled) {
            return Flux.empty();
        }
        return Flux.just(new TraceEvent(
                "semantic-context", null, "semantic-context", TraceStatus.END,
                Map.of("subjects", semanticLayer.toTracePayload())
        ));
    }

    private IntentStage resolveIntent(BiQueryRequest request) {
        PendingClarification context = clarificationService.loadContext(request.sessionId());
        if (request.sessionId() != null && context == null) {
            log.info("SessionId {} not found, falling back to fresh query", request.sessionId());
        }

        List<TraceEvent> traces = new ArrayList<>();
        Consumer<TraceEvent> emitter = traceEnabled ? traces::add : e -> {};

        IntentExtractionResult result = intentExtractionService.extractWithContext(
                request.question(), context, request.credentialId(), request.modelName(), emitter);
        return new IntentStage(context, result, traces);
    }

    private Flux<SseEvent> continueWorkflow(BiQueryRequest request, IntentStage stage) {
        if (stage.result() instanceof IntentExtractionResult.NeedsClarification clarification) {
            List<SseEvent> events = new ArrayList<>();
            if (traceEnabled) {
                events.addAll(stage.traces());
            }
            events.add(clarificationService.createEvent(clarification, request.question(),
                    request.sessionId(), stage.context()));
            events.add(new DoneEvent());
            return Flux.fromIterable(events);
        }

        IntentExtractionResult.Ready ready = (IntentExtractionResult.Ready) stage.result();
        BiQueryPlan plan = queryAssemblyService.assemble(ready);

        log.info("Extracted intent: subject={}, metrics={}, dimensions={}, filters={}",
                plan.intent().getSubject(),
                plan.intent().getMetrics(),
                plan.intent().getDimensions(),
                plan.intent().getFilters());

        List<SseEvent> preStream = new ArrayList<>();
        if (traceEnabled) {
            preStream.addAll(stage.traces());
            preStream.add(sqlBuildEndTrace());
        }
        preStream.add(queryAssemblyService.toIntentEvent(plan));
        preStream.add(new StatusEvent("querying", "正在查询数据库..."));
        if (traceEnabled) {
            preStream.add(queryExecuteStartTrace());
        }

        Flux<SseEvent> queryAndInsight = Mono.fromCallable(() -> queryExecutionService.execute(plan.sqlResult()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(data -> {
                    List<SseEvent> postExecute = new ArrayList<>();
                    if (traceEnabled) {
                        postExecute.add(queryExecuteEndTrace(data.size()));
                    }
                    postExecute.add(new StatusEvent("insight",
                            "查询到 " + data.size() + " 条记录，正在生成洞察..."));
                    if (traceEnabled) {
                        postExecute.add(insightGenerationStartTrace());
                    }

                    Flux<SseEvent> insightStream = insightStreamAssembler.assemble(
                            insightGenerationService.generateStream(
                                    request.question(),
                                    data,
                                    request.credentialId(),
                                    request.modelName()
                            ),
                            data
                    );

                    if (traceEnabled) {
                        insightStream = insertInsightEndBeforeResult(insightStream);
                    }

                    return Flux.concat(Flux.fromIterable(postExecute), insightStream);
                });

        return Flux.concat(Flux.fromIterable(preStream), queryAndInsight);
    }

    private Flux<SseEvent> insertInsightEndBeforeResult(Flux<SseEvent> insightStream) {
        return insightStream.concatMap(event -> {
            if (event instanceof ResultEvent re) {
                return Flux.just(insightGenerationEndTrace(re.chartType()), event);
            }
            return Flux.just(event);
        });
    }

    private TraceEvent sqlBuildEndTrace() {
        return new TraceEvent("sql-build", null, "sql-build", TraceStatus.END, null);
    }

    private TraceEvent queryExecuteStartTrace() {
        return new TraceEvent("query-execute", null, "query-execute", TraceStatus.START, null);
    }

    private TraceEvent queryExecuteEndTrace(int rowCount) {
        return new TraceEvent(
                "query-execute", null, "query-execute", TraceStatus.END,
                Map.of("rowCount", rowCount)
        );
    }

    private TraceEvent insightGenerationStartTrace() {
        return new TraceEvent(
                "insight-generation", null, "insight-generation", TraceStatus.START, null);
    }

    private TraceEvent insightGenerationEndTrace(String chartType) {
        return new TraceEvent(
                "insight-generation", null, "insight-generation", TraceStatus.END,
                Map.of("chartType", chartType == null ? "bar" : chartType)
        );
    }

    private record IntentStage(PendingClarification context, IntentExtractionResult result,
                                List<TraceEvent> traces) {
    }
}
