package org.ai4j.factory.bi.intent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({
        @JsonSubTypes.Type(value = IntentExtractionResult.Ready.class, name = "ready"),
        @JsonSubTypes.Type(value = IntentExtractionResult.NeedsClarification.class, name = "needs_clarification"),
})
public sealed interface IntentExtractionResult {

    record Ready(
            String subject,
            List<String> metrics,
            List<String> dimensions,
            List<Filter> filters
    ) implements IntentExtractionResult {
        public Ready {
            metrics = metrics == null ? List.of() : metrics;
            dimensions = dimensions == null ? List.of() : dimensions;
            filters = filters == null ? List.of() : filters;
        }
    }

    record NeedsClarification(
            String reason,
            String message,
            String subject,
            String metric
    ) implements IntentExtractionResult {}
}
