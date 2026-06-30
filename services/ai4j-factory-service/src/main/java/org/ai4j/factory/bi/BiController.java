package org.ai4j.factory.bi;

import org.ai4j.factory.bi.insight.InsightGenerationService;
import org.ai4j.factory.bi.intent.IntentExtractionService;
import org.ai4j.factory.bi.intent.QueryIntent;
import org.ai4j.factory.bi.query.QueryExecutionService;
import org.ai4j.factory.bi.query.SqlBuilder;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.sse.ChunkEvent;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bi")
public class BiController {

    private static final Logger log = LoggerFactory.getLogger(BiController.class);

    private final IntentExtractionService intentService;
    private final QueryExecutionService queryService;
    private final InsightGenerationService insightService;
    private final SemanticLayer semanticLayer;

    public BiController(IntentExtractionService intentService,
                        QueryExecutionService queryService,
                        InsightGenerationService insightService,
                        SemanticLayer semanticLayer) {
        this.intentService = intentService;
        this.queryService = queryService;
        this.insightService = insightService;
        this.semanticLayer = semanticLayer;
    }

    @PostMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> query(@RequestBody QueryRequest request) {
        log.info("BI query: {}", request.question());

        return Flux.create(sink -> {
            StringBuilder fullText = new StringBuilder();
            int[] lastEmitted = {0};

            try {
                sink.next(SseEventSerializer.toJson(new StatusEvent("analyzing", "正在分析你的问题...")));

                QueryIntent intent = intentService.extract(request.question(), request.credentialId(), request.modelName());
                log.info("Extracted intent: subject={}, metrics={}, dimensions={}, filters={}",
                        intent.getSubject(), intent.getMetrics(), intent.getDimensions(), intent.getFilters());
                sink.next(SseEventSerializer.toJson(toIntentEvent(intent)));

                sink.next(SseEventSerializer.toJson(new StatusEvent("querying", "正在查询数据库...")));

                Subject subject = semanticLayer.getSubject(intent.getSubject());
                SqlBuilder.SqlResult sqlResult = new SqlBuilder().build(intent, subject);
                List<Map<String, Object>> data = queryService.execute(sqlResult);

                sink.next(SseEventSerializer.toJson(new StatusEvent("insight",
                        "查询到 " + data.size() + " 条记录，正在生成洞察...")));

                insightService.generateStream(request.question(), data, request.credentialId(), request.modelName())
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

    private IntentEvent toIntentEvent(QueryIntent intent) {
        List<Map<String, Object>> filters = intent.getFilters().stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("dimension", f.getDimension());
                    m.put("operator", f.getOperator());
                    m.put("value", f.getValue());
                    return m;
                })
                .toList();
        return new IntentEvent(intent.getSubject(), intent.getMetrics(), intent.getDimensions(), filters);
    }

    public record QueryRequest(String question, Long credentialId, String modelName) {}
}
