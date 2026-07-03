package org.ai4j.factory.bi.insight;

import org.ai4j.factory.sse.DoneEvent;
import org.ai4j.factory.sse.ResultEvent;
import org.ai4j.factory.sse.SseEvent;
import org.ai4j.factory.sse.TypingTextProjector;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
public class InsightStreamAssembler {

    private static final String CHART_MARKER = "<<CHART:";

    private final TypingTextProjector typingTextProjector;

    public InsightStreamAssembler(TypingTextProjector typingTextProjector) {
        this.typingTextProjector = typingTextProjector;
    }

    public Flux<SseEvent> assemble(Flux<String> rawStream, List<Map<String, Object>> data) {
        return Flux.defer(() -> {
            ParsedInsightStream parser = new ParsedInsightStream();
            Flux<String> visibleTextStream = rawStream.concatMap(parser::append)
                    .concatWith(Flux.defer(() -> Flux.fromIterable(parser.complete())));

            return typingTextProjector.project(visibleTextStream)
                    .cast(SseEvent.class)
                    .concatWith(Flux.defer(() -> Flux.just(
                            new ResultEvent(parser.chartType(), data, data.size()),
                            new DoneEvent()
                    )));
        });
    }

    private static final class ParsedInsightStream {

        private final StringBuilder fullText = new StringBuilder();
        private int lastEmitted;

        private Flux<String> append(String chunk) {
            fullText.append(chunk);
            int safeLength = safeDisplayLength();
            if (safeLength <= lastEmitted) {
                return Flux.empty();
            }
            String delta = fullText.substring(lastEmitted, safeLength);
            lastEmitted = safeLength;
            return Flux.just(delta);
        }

        private List<String> complete() {
            int safeLength = safeDisplayLength();
            if (safeLength <= lastEmitted) {
                return List.of();
            }
            String delta = fullText.substring(lastEmitted, safeLength);
            lastEmitted = safeLength;
            return List.of(delta);
        }

        private String chartType() {
            int markerStart = fullText.lastIndexOf(CHART_MARKER);
            if (markerStart >= 0) {
                int valueStart = markerStart + CHART_MARKER.length();
                int valueEnd = fullText.indexOf(">>", valueStart);
                if (valueEnd > valueStart) {
                    return fullText.substring(valueStart, valueEnd).trim();
                }
            }
            return "bar";
        }

        private int safeDisplayLength() {
            int markerStart = fullText.lastIndexOf(CHART_MARKER);
            if (markerStart >= 0) {
                return markerStart;
            }
            int holdback = partialMarkerPrefixLength();
            return fullText.length() - holdback;
        }

        private int partialMarkerPrefixLength() {
            int maxLength = Math.min(fullText.length(), CHART_MARKER.length());
            for (int length = maxLength; length >= 1; length--) {
                String suffix = fullText.substring(fullText.length() - length);
                if (CHART_MARKER.startsWith(suffix)) {
                    return length;
                }
            }
            return 0;
        }
    }
}
