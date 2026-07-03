package org.ai4j.factory.bi;

import org.ai4j.factory.sse.SseEvent;
import org.ai4j.factory.sse.SseEventSerializer;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/bi")
public class BiController {

    private final BiQueryWorkflowService workflowService;

    public BiController(BiQueryWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> query(@RequestParam String question,
                                                               @RequestParam Long credentialId,
                                                               @RequestParam(required = false) String modelName,
                                                               @RequestParam(required = false) String sessionId) {
        Flux<ServerSentEvent<String>> body = workflowService.stream(
                        new BiQueryRequest(question, credentialId, modelName, sessionId)
                )
                .map(this::sse);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore().mustRevalidate().sMaxAge(0, TimeUnit.SECONDS))
                .header("X-Accel-Buffering", "no")
                .header("Connection", "keep-alive")
                .body(body);
    }

    private ServerSentEvent<String> sse(SseEvent event) {
        return SseEventSerializer.toServerSentEvent(event);
    }
}
