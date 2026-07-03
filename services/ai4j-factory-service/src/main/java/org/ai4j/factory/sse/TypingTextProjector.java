package org.ai4j.factory.sse;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class TypingTextProjector {

    public Flux<ChunkEvent> project(Flux<String> textStream) {
        return textStream.concatMap(this::splitChunk);
    }

    private Flux<ChunkEvent> splitChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromStream(chunk.codePoints().mapToObj(codePoint -> new ChunkEvent(Character.toString(codePoint))));
    }
}
