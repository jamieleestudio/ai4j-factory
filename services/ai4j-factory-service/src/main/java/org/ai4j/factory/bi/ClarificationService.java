package org.ai4j.factory.bi;

import org.ai4j.factory.bi.clarification.ClarificationStore;
import org.ai4j.factory.bi.clarification.PendingClarification;
import org.ai4j.factory.bi.intent.IntentExtractionResult;
import org.ai4j.factory.bi.semantic.Metric;
import org.ai4j.factory.bi.semantic.SemanticLayer;
import org.ai4j.factory.bi.semantic.Subject;
import org.ai4j.factory.sse.ClarificationEvent;
import org.ai4j.factory.sse.ClarificationOption;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ClarificationService {

    private final ClarificationStore clarificationStore;
    private final SemanticLayer semanticLayer;

    public ClarificationService(ClarificationStore clarificationStore, SemanticLayer semanticLayer) {
        this.clarificationStore = clarificationStore;
        this.semanticLayer = semanticLayer;
    }

    public PendingClarification loadContext(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return clarificationStore.get(sessionId).orElse(null);
    }

    public ClarificationEvent createEvent(IntentExtractionResult.NeedsClarification clarification,
                                          String question,
                                          String sessionId,
                                          PendingClarification context) {
        List<ClarificationOption> options = buildOptions(clarification);
        String currentSessionId = context != null ? sessionId : UUID.randomUUID().toString();
        String originalQuestion = context != null ? context.originalQuestion() : question;

        PendingClarification pending = new PendingClarification(
                originalQuestion,
                clarification.reason(),
                options,
                clarification.subject(),
                Instant.now()
        );
        clarificationStore.put(currentSessionId, pending);
        return new ClarificationEvent(currentSessionId, clarification.message(), options);
    }

    private List<ClarificationOption> buildOptions(IntentExtractionResult.NeedsClarification clarification) {
        List<ClarificationOption> options = new ArrayList<>();
        switch (clarification.reason()) {
            case "question_unclear" -> semanticLayer.getAllSubjects().forEach(subject -> options.add(toSubjectOption(subject)));
            case "subject_ambiguous" -> {
                String metricName = clarification.metric();
                semanticLayer.getAllSubjects().stream()
                        .filter(subject -> subject.getMetrics().stream().anyMatch(metric -> metric.getName().equals(metricName)))
                        .map(this::toSubjectOption)
                        .forEach(options::add);
            }
            case "metric_unspecified" -> {
                Subject subject = semanticLayer.getSubject(clarification.subject());
                for (Metric metric : subject.getMetrics()) {
                    options.add(new ClarificationOption(metric.getName(), metric.getName(), metric.getDescription()));
                }
            }
            default -> semanticLayer.getAllSubjects().forEach(subject -> options.add(toSubjectOption(subject)));
        }
        return options;
    }

    private ClarificationOption toSubjectOption(Subject subject) {
        return new ClarificationOption(subject.getName(), subject.getName(), subject.getDescription());
    }
}
