package org.ai4j.factory.bi;

import org.ai4j.factory.bi.clarification.ClarificationStore;
import org.ai4j.factory.bi.clarification.PendingClarification;
import org.ai4j.factory.bi.insight.InsightGenerationService;
import org.ai4j.factory.bi.intent.IntentExtractionResult;
import org.ai4j.factory.bi.intent.IntentExtractionService;
import org.ai4j.factory.bi.intent.QueryIntent;
import org.ai4j.factory.bi.query.QueryExecutionService;
import org.ai4j.factory.bi.query.SqlBuilder;
import org.ai4j.factory.bi.semantic.Dimension;
import org.ai4j.factory.bi.semantic.Metric;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.sse.ChunkEvent;
import org.ai4j.factory.sse.ClarificationEvent;
import org.ai4j.factory.sse.ClarificationOption;
import org.ai4j.factory.sse.DimensionRef;
import org.ai4j.factory.sse.DoneEvent;
import org.ai4j.factory.sse.ErrorEvent;
import org.ai4j.factory.sse.IntentEvent;
import org.ai4j.factory.sse.ResultEvent;
import org.ai4j.factory.sse.SseEventSerializer;
import org.ai4j.factory.sse.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bi")
public class BiController {

    private static final Logger log = LoggerFactory.getLogger(BiController.class);

    private final IntentExtractionService intentService;
    private final QueryExecutionService queryService;
    private final InsightGenerationService insightService;
    private final SemanticLayer semanticLayer;
    private final ClarificationStore clarificationStore;

    public BiController(IntentExtractionService intentService,
                        QueryExecutionService queryService,
                        InsightGenerationService insightService,
                        SemanticLayer semanticLayer,
                        ClarificationStore clarificationStore) {
        this.intentService = intentService;
        this.queryService = queryService;
        this.insightService = insightService;
        this.semanticLayer = semanticLayer;
        this.clarificationStore = clarificationStore;
    }

    @GetMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> query(@RequestParam String question,
                              @RequestParam Long credentialId,
                              @RequestParam(required = false) String modelName,
                              @RequestParam(required = false) String sessionId) {
        log.info("BI query: {}, sessionId: {}", question, sessionId);

        return Flux.create(sink -> {
            StringBuilder fullText = new StringBuilder();
            int[] lastEmitted = {0};

            try {
                sink.next(SseEventSerializer.toJson(new StatusEvent("analyzing", "正在分析你的问题...")));

                PendingClarification context = null;
                if (sessionId != null) {
                    context = clarificationStore.get(sessionId).orElse(null);
                    if (context == null) {
                        log.info("SessionId {} not found, falling back to fresh query", sessionId);
                    }
                }

                IntentExtractionResult result = context != null
                        ? intentService.extractWithContext(question, context,
                                credentialId, modelName)
                        : intentService.extract(question,
                                credentialId, modelName);

                if (result instanceof IntentExtractionResult.NeedsClarification nc) {
                    handleClarification(sink, nc, question, sessionId, credentialId, modelName, context);
                    return;
                }

                IntentExtractionResult.Ready ready = (IntentExtractionResult.Ready) result;
                QueryIntent intent = toQueryIntent(ready);
                log.info("Extracted intent: subject={}, metrics={}, dimensions={}, filters={}",
                        intent.getSubject(), intent.getMetrics(), intent.getDimensions(), intent.getFilters());

                Subject subject = semanticLayer.getSubject(intent.getSubject());

                sink.next(SseEventSerializer.toJson(toIntentEvent(intent, subject)));

                sink.next(SseEventSerializer.toJson(new StatusEvent("querying", "正在查询数据库...")));

                SqlBuilder.SqlResult sqlResult = new SqlBuilder().build(intent, subject);
                List<Map<String, Object>> data = queryService.execute(sqlResult);

                sink.next(SseEventSerializer.toJson(new StatusEvent("insight",
                        "查询到 " + data.size() + " 条记录，正在生成洞察...")));

                insightService.generateStream(question, data, credentialId, modelName)
                        .doOnNext(chunk -> {
                            fullText.append(chunk);
                            int safeLen = insightService.safeDisplayLength(fullText.toString());
                            if (safeLen > lastEmitted[0]) {
                                String delta = fullText.substring(lastEmitted[0], safeLen);
                                sink.next(SseEventSerializer.toJson(new ChunkEvent(delta)));
                                lastEmitted[0] = safeLen;
                            }
                        })
                        .doOnComplete(() -> {
                            int safeLen = insightService.safeDisplayLength(fullText.toString());
                            if (safeLen > lastEmitted[0]) {
                                sink.next(SseEventSerializer.toJson(
                                        new ChunkEvent(fullText.substring(lastEmitted[0], safeLen))));
                                lastEmitted[0] = safeLen;
                            }
                            String chartType = insightService.extractChartType(fullText.toString());
                            sink.next(SseEventSerializer.toJson(new ResultEvent(chartType, data, data.size())));
                            sink.next(SseEventSerializer.toJson(new DoneEvent()));
                            sink.complete();
                        })
                        .doOnError(sink::error)
                        .subscribe();

            } catch (Exception e) {
                log.error("BI query failed", e);
                sink.next(SseEventSerializer.toJson(new ErrorEvent(e.getMessage())));
                sink.next(SseEventSerializer.toJson(new DoneEvent()));
                sink.complete();
            }
        });
    }

    private void handleClarification(reactor.core.publisher.FluxSink<String> sink,
                                      IntentExtractionResult.NeedsClarification nc,
                                      String question,
                                      String sessionIdParam,
                                      Long credentialId,
                                      String modelName,
                                      PendingClarification context) {
        List<ClarificationOption> options = buildClarificationOptions(nc);

        String sessionId;
        String originalQuestion;
        if (context != null) {
            sessionId = sessionIdParam;
            originalQuestion = context.originalQuestion();
        } else {
            sessionId = UUID.randomUUID().toString();
            originalQuestion = question;
        }

        PendingClarification pending = new PendingClarification(
                originalQuestion, nc.reason(), options, nc.subject(), Instant.now()
        );
        clarificationStore.put(sessionId, pending);

        sink.next(SseEventSerializer.toJson(new ClarificationEvent(sessionId, nc.message(), options)));
        sink.next(SseEventSerializer.toJson(new DoneEvent()));
        sink.complete();
    }

    private List<ClarificationOption> buildClarificationOptions(IntentExtractionResult.NeedsClarification nc) {
        List<ClarificationOption> options = new ArrayList<>();
        switch (nc.reason()) {
            case "question_unclear" -> {
                for (Subject subject : semanticLayer.getAllSubjects()) {
                    options.add(new ClarificationOption(
                            subject.getName(), subject.getName(), subject.getDescription()));
                }
            }
            case "subject_ambiguous" -> {
                String metricName = nc.metric();
                for (Subject subject : semanticLayer.getAllSubjects()) {
                    boolean hasMetric = subject.getMetrics().stream()
                            .anyMatch(m -> m.getName().equals(metricName));
                    if (hasMetric) {
                        options.add(new ClarificationOption(
                                subject.getName(), subject.getName(), subject.getDescription()));
                    }
                }
            }
            case "metric_unspecified" -> {
                Subject subject = semanticLayer.getSubject(nc.subject());
                for (Metric metric : subject.getMetrics()) {
                    options.add(new ClarificationOption(
                            metric.getName(), metric.getName(), metric.getDescription()));
                }
            }
            default -> {
                for (Subject subject : semanticLayer.getAllSubjects()) {
                    options.add(new ClarificationOption(
                            subject.getName(), subject.getName(), subject.getDescription()));
                }
            }
        }
        return options;
    }

    private QueryIntent toQueryIntent(IntentExtractionResult.Ready ready) {
        QueryIntent intent = new QueryIntent();
        intent.setSubject(ready.subject());
        intent.setMetrics(new ArrayList<>(ready.metrics()));
        intent.setDimensions(new ArrayList<>(ready.dimensions()));
        intent.setFilters(new ArrayList<>(ready.filters()));
        return intent;
    }

    private IntentEvent toIntentEvent(QueryIntent intent, Subject subject) {
        List<Map<String, Object>> filters = intent.getFilters().stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("dimension", f.getDimension());
                    m.put("operator", f.getOperator());
                    m.put("value", f.getValue());
                    return m;
                })
                .toList();
        List<DimensionRef> dimensions = intent.getDimensions().stream()
                .map(name -> {
                    Dimension dim = subject.getDimensions().stream()
                            .filter(d -> d.getName().equals(name))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Dimension not found: " + name));
                    return new DimensionRef(name, dim.getType().name());
                })
                .toList();
        return new IntentEvent(intent.getSubject(), intent.getMetrics(), dimensions, filters);
    }
}
