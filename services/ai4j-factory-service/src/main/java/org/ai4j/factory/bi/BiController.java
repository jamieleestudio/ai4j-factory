package org.ai4j.factory.bi;

import org.ai4j.factory.bi.insight.InsightGenerationService;
import org.ai4j.factory.bi.insight.InsightResponse;
import org.ai4j.factory.bi.intent.IntentExtractionService;
import org.ai4j.factory.bi.intent.QueryIntent;
import org.ai4j.factory.bi.query.QueryExecutionService;
import org.ai4j.factory.bi.query.SqlBuilder;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/query")
    public InsightResponse query(@RequestBody QueryRequest request) {
        log.info("BI query: {}", request.question());

        QueryIntent intent = intentService.extract(request.question(), request.credentialId(), request.modelName());
        log.info("Extracted intent: subject={}, metrics={}, dimensions={}, filters={}",
                intent.getSubject(), intent.getMetrics(), intent.getDimensions(), intent.getFilters());

        Subject subject = semanticLayer.getSubject(intent.getSubject());
        SqlBuilder.SqlResult sqlResult = new SqlBuilder().build(intent, subject);

        List<Map<String, Object>> data = queryService.execute(sqlResult);

        return insightService.generate(request.question(), data, request.credentialId(), request.modelName());
    }

    public record QueryRequest(String question, Long credentialId, String modelName) {}
}
