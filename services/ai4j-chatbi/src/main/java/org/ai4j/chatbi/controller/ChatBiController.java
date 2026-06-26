package org.ai4j.chatbi.controller;

import org.ai4j.chatbi.insight.InsightGenerationService;
import org.ai4j.chatbi.insight.InsightResponse;
import org.ai4j.chatbi.intent.IntentExtractionService;
import org.ai4j.chatbi.intent.QueryIntent;
import org.ai4j.chatbi.query.QueryExecutionService;
import org.ai4j.chatbi.query.SqlBuilder;
import org.ai4j.chatbi.semantic.SemanticLayer;
import org.ai4j.chatbi.semantic.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chatbi")
public class ChatBiController {

    private static final Logger log = LoggerFactory.getLogger(ChatBiController.class);

    private final IntentExtractionService intentService;
    private final QueryExecutionService queryService;
    private final InsightGenerationService insightService;
    private final SemanticLayer semanticLayer;

    public ChatBiController(IntentExtractionService intentService,
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
        log.info("ChatBI query: {}", request.question());

        QueryIntent intent = intentService.extract(request.question());
        log.info("Extracted intent: subject={}, metrics={}, dimensions={}, filters={}",
                intent.getSubject(), intent.getMetrics(), intent.getDimensions(), intent.getFilters());

        Subject subject = semanticLayer.getSubject(intent.getSubject());
        SqlBuilder.SqlResult sqlResult = new SqlBuilder().build(intent, subject);

        List<Map<String, Object>> data = queryService.execute(sqlResult);

        return insightService.generate(request.question(), data);
    }

    public record QueryRequest(String question) {}
}
