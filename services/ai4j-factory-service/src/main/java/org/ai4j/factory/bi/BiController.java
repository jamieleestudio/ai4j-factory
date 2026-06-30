package org.ai4j.factory.bi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ai4j.factory.bi.insight.InsightGenerationService;
import org.ai4j.factory.bi.intent.IntentExtractionService;
import org.ai4j.factory.bi.intent.QueryIntent;
import org.ai4j.factory.bi.query.QueryExecutionService;
import org.ai4j.factory.bi.query.SqlBuilder;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
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
    private final ObjectMapper objectMapper;

    public BiController(IntentExtractionService intentService,
                        QueryExecutionService queryService,
                        InsightGenerationService insightService,
                        SemanticLayer semanticLayer) {
        this.intentService = intentService;
        this.queryService = queryService;
        this.insightService = insightService;
        this.semanticLayer = semanticLayer;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> query(@RequestBody QueryRequest request) {
        log.info("BI query: {}", request.question());

        return Flux.create(sink -> {
            try {
                sink.next("[progress] 正在分析你的问题...");

                QueryIntent intent = intentService.extract(request.question(), request.credentialId(), request.modelName());
                log.info("Extracted intent: subject={}, metrics={}, dimensions={}, filters={}",
                        intent.getSubject(), intent.getMetrics(), intent.getDimensions(), intent.getFilters());

                sink.next("[progress] 正在查询数据库...");

                Subject subject = semanticLayer.getSubject(intent.getSubject());
                SqlBuilder.SqlResult sqlResult = new SqlBuilder().build(intent, subject);
                List<Map<String, Object>> data = queryService.execute(sqlResult);

                sink.next("[progress] 查询到 " + data.size() + " 条记录，正在生成洞察...");

                StringBuilder fullText = new StringBuilder();

                insightService.generateStream(request.question(), data, request.credentialId(), request.modelName())
                        .doOnNext(chunk -> {
                            fullText.append(chunk);
                            sink.next("[chunk] " + chunk);
                        })
                        .doOnComplete(() -> {
                            String chartType = insightService.extractChartType(fullText.toString());
                            sink.next("[result] " + buildResultJson(chartType, data));
                            sink.complete();
                        })
                        .doOnError(sink::error)
                        .subscribe();

            } catch (Exception e) {
                log.error("BI query failed", e);
                sink.next("[error] " + e.getMessage());
                sink.complete();
            }
        });
    }

    private String buildResultJson(String chartType, List<Map<String, Object>> data) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chartType", chartType);
            result.put("data", data);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Failed to serialize result", e);
            return "{\"chartType\":\"single_value\",\"data\":[]}";
        }
    }

    public record QueryRequest(String question, Long credentialId, String modelName) {}
}
