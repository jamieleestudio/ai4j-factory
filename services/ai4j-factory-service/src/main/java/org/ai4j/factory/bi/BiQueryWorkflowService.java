package org.ai4j.factory.bi;

import org.ai4j.factory.bi.clarification.PendingClarification;
import org.ai4j.factory.bi.insight.InsightGenerationService;
import org.ai4j.factory.bi.insight.InsightStreamAssembler;
import org.ai4j.factory.bi.intent.IntentExtractionResult;
import org.ai4j.factory.bi.intent.IntentExtractionService;
import org.ai4j.factory.bi.query.QueryExecutionService;
import org.ai4j.factory.sse.DoneEvent;
import org.ai4j.factory.sse.ErrorEvent;
import org.ai4j.factory.sse.SseEvent;
import org.ai4j.factory.sse.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class BiQueryWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(BiQueryWorkflowService.class);

    private final IntentExtractionService intentExtractionService;
    private final QueryExecutionService queryExecutionService;
    private final InsightGenerationService insightGenerationService;
    private final InsightStreamAssembler insightStreamAssembler;
    private final QueryAssemblyService queryAssemblyService;
    private final ClarificationService clarificationService;

    public BiQueryWorkflowService(IntentExtractionService intentExtractionService,
                                  QueryExecutionService queryExecutionService,
                                  InsightGenerationService insightGenerationService,
                                  InsightStreamAssembler insightStreamAssembler,
                                  QueryAssemblyService queryAssemblyService,
                                  ClarificationService clarificationService) {
        this.intentExtractionService = intentExtractionService;
        this.queryExecutionService = queryExecutionService;
        this.insightGenerationService = insightGenerationService;
        this.insightStreamAssembler = insightStreamAssembler;
        this.queryAssemblyService = queryAssemblyService;
        this.clarificationService = clarificationService;
    }

    public Flux<SseEvent> stream(BiQueryRequest request) {
        log.info("BI query: {}, sessionId: {}", request.question(), request.sessionId());

        return Flux.concat(
                Flux.just(new StatusEvent("analyzing", "正在分析你的问题...")),
                Mono.fromCallable(() -> resolveIntent(request))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(stage -> continueWorkflow(request, stage))
        ).onErrorResume(error -> {
            log.error("BI query failed", error);
            return Flux.just(new ErrorEvent(error.getMessage()), new DoneEvent());
        });
    }

    private IntentStage resolveIntent(BiQueryRequest request) {
        PendingClarification context = clarificationService.loadContext(request.sessionId());
        if (request.sessionId() != null && context == null) {
            log.info("SessionId {} not found, falling back to fresh query", request.sessionId());
        }

        IntentExtractionResult result = context != null
                ? intentExtractionService.extractWithContext(request.question(), context, request.credentialId(), request.modelName())
                : intentExtractionService.extract(request.question(), request.credentialId(), request.modelName());
        return new IntentStage(context, result);
    }

    private Flux<SseEvent> continueWorkflow(BiQueryRequest request, IntentStage stage) {
        if (stage.result() instanceof IntentExtractionResult.NeedsClarification clarification) {
            return Flux.just(
                    clarificationService.createEvent(clarification, request.question(), request.sessionId(), stage.context()),
                    new DoneEvent()
            );
        }

        IntentExtractionResult.Ready ready = (IntentExtractionResult.Ready) stage.result();
        BiQueryPlan plan = queryAssemblyService.assemble(ready);

        log.info("Extracted intent: subject={}, metrics={}, dimensions={}, filters={}",
                plan.intent().getSubject(),
                plan.intent().getMetrics(),
                plan.intent().getDimensions(),
                plan.intent().getFilters());

        return Flux.concat(
                Flux.just(
                        queryAssemblyService.toIntentEvent(plan),
                        new StatusEvent("querying", "正在查询数据库...")
                ),
                Mono.fromCallable(() -> queryExecutionService.execute(plan.sqlResult()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(data -> Flux.concat(
                                Flux.just(new StatusEvent("insight", "查询到 " + data.size() + " 条记录，正在生成洞察...")),
                                insightStreamAssembler.assemble(
                                        insightGenerationService.generateStream(
                                                request.question(),
                                                data,
                                                request.credentialId(),
                                                request.modelName()
                                        ),
                                        data
                                )
                        ))
        );
    }

    private record IntentStage(PendingClarification context, IntentExtractionResult result) {
    }
}
